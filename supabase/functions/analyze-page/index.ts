import { createClient } from "npm:@supabase/supabase-js@2";

const DAILY_LIMIT = Number(Deno.env.get("ANALYZE_DAILY_LIMIT") ?? "60");

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const PROMPT = `A student uploaded this image to their study app. Decide whether it shows study material: a page from a textbook, printed or handwritten notes, a worksheet or an exercise sheet.
If it is anything else, such as a selfie, a person, a meme, a social media screenshot, a game, food, a random object or anything inappropriate, set is_study_material to false and give a short neutral reason. Do not describe people or inappropriate content.
If it is study material, read it and fill the other fields. Subject must be one of: Maths, Science, Physics, Chemistry, Biology, English, Nepali, Social Studies, Computer, Accountancy, Economics, Other.
estimated_study_minutes is how long a student needs to study this page properly, between 10 and 90.
readable is false if the page is too blurry, dark or cut off to read.`;

const SCHEMA = {
  type: "OBJECT",
  properties: {
    is_study_material: { type: "BOOLEAN" },
    reason: { type: "STRING" },
    readable: { type: "BOOLEAN" },
    subject: { type: "STRING" },
    chapter_or_topic: { type: "STRING" },
    topics: { type: "ARRAY", items: { type: "STRING" } },
    summary: { type: "STRING" },
    difficulty: { type: "STRING", enum: ["easy", "medium", "hard"] },
    estimated_study_minutes: { type: "INTEGER" },
    has_questions: { type: "BOOLEAN" },
  },
  required: ["is_study_material", "reason"],
};

const JSON_SHAPE = `Reply with only a JSON object with these keys: is_study_material (boolean), reason (string), readable (boolean), subject (string), chapter_or_topic (string), topics (array of up to 6 short strings), summary (one or two sentences), difficulty ("easy", "medium" or "hard"), estimated_study_minutes (integer from 10 to 90), has_questions (boolean).`;

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const GROQ_KEY = Deno.env.get("GROQ_API_KEY") ?? "";
const NVIDIA_KEY = Deno.env.get("NVIDIA_KEY") ?? "";
const GEMINI_MODELS = [Deno.env.get("GEMINI_MODEL") ?? "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash"];

const modelCache: Record<string, string[]> = {};

async function listModels(kind: "groq" | "nvidia"): Promise<string[]> {
  if (modelCache[kind]) return modelCache[kind];
  const url = kind === "groq" ? "https://api.groq.com/openai/v1/models" : "https://integrate.api.nvidia.com/v1/models";
  const key = kind === "groq" ? GROQ_KEY : NVIDIA_KEY;
  const r = await fetch(url, { headers: { Authorization: `Bearer ${key}` }, signal: AbortSignal.timeout(10000) });
  if (!r.ok) throw new Error(`${kind} models ${r.status}`);
  const j = await r.json();
  const ids: string[] = (j.data ?? []).map((m: { id: string }) => String(m.id));
  console.log(`${kind} models: ${ids.join(", ").slice(0, 1800)}`);
  modelCache[kind] = ids;
  return ids;
}

function rank(ids: string[], prefs: RegExp[]): string[] {
  const skip = /whisper|orpheus|guard|safeguard|tts|embed|rerank|reward/i;
  const out: string[] = [];
  for (const re of prefs) for (const id of ids) if (re.test(id) && !skip.test(id) && !out.includes(id)) out.push(id);
  return out;
}

type OA = { role: string; content: unknown };

async function chat(kind: "groq" | "nvidia", model: string, messages: OA[], maxTokens: number, timeoutMs: number): Promise<string> {
  const url = kind === "groq" ? "https://api.groq.com/openai/v1/chat/completions" : "https://integrate.api.nvidia.com/v1/chat/completions";
  const key = kind === "groq" ? GROQ_KEY : NVIDIA_KEY;
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify({ model, messages, temperature: 0.4, max_tokens: maxTokens }),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!r.ok) throw new Error(`${kind} ${model} ${r.status} ${(await r.text()).slice(0, 160)}`);
  const j = await r.json();
  const text = String(j.choices?.[0]?.message?.content ?? "").replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  if (!text) throw new Error(`${kind} ${model} empty`);
  return text;
}

async function viaProvider(kind: "groq" | "nvidia", prefs: RegExp[], messages: OA[], maxTokens: number, timeoutMs: number, first?: string): Promise<string> {
  if ((kind === "groq" ? GROQ_KEY : NVIDIA_KEY) === "") throw new Error(`${kind} key missing`);
  const ids = rank(await listModels(kind), prefs);
  if (first) ids.unshift(first);
  if (!ids.length) throw new Error(`${kind} no matching model`);
  let last: unknown = new Error(`${kind} failed`);
  for (const id of ids.slice(0, 3)) {
    try {
      return await chat(kind, id, messages, maxTokens, timeoutMs);
    } catch (e) {
      last = e;
      console.error(String(e));
    }
  }
  throw last;
}

async function viaGemini(parts: unknown[], system: string | null, history: { role: string; parts: unknown[] }[], maxTokens: number, extra: Record<string, unknown> = {}): Promise<string> {
  if (!GEMINI_KEY) throw new Error("gemini key missing");
  let last = "no model";
  for (const model of GEMINI_MODELS) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_KEY },
      signal: AbortSignal.timeout(30000),
      body: JSON.stringify({
        ...(system ? { system_instruction: { parts: [{ text: system }] } } : {}),
        contents: [...history, { role: "user", parts }],
        generationConfig: { temperature: 0.4, maxOutputTokens: maxTokens, ...extra },
      }),
    }).catch((e) => ({ ok: false, status: 0, text: async () => String(e), json: async () => ({}) }) as unknown as Response);
    if (!r.ok) {
      last = `gemini ${model} ${r.status} ${(await r.text()).slice(0, 120)}`;
      console.error(last);
      continue;
    }
    const j = await r.json();
    const text = (j.candidates?.[0]?.content?.parts ?? []).map((p: { text?: string }) => p.text ?? "").join("").trim();
    if (text) return text;
    last = `gemini ${model} empty`;
  }
  throw new Error(last);
}

const VISION_PREFS = [/llama-4/, /vision/, /qwen3/, /muse/];

function firstJson(text: string) {
  const m = text.match(/\{[\s\S]*\}/);
  if (!m) throw new Error("no json");
  return JSON.parse(m[0]);
}

async function analyse(image: string, hint: string) {
  const prompt = `${PROMPT}\n${hint ? `The student says the subject is: ${hint}.\n` : ""}${JSON_SHAPE}`;
  const msgs: OA[] = [{
    role: "user",
    content: [{ type: "text", text: prompt }, { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } }],
  }];
  const errors: string[] = [];
  try {
    return firstJson(await viaProvider("groq", VISION_PREFS, msgs, 900, 30000));
  } catch (e) {
    errors.push(String(e).slice(0, 200));
  }
  try {
    const text = await viaGemini(
      [{ text: prompt }, { inline_data: { mime_type: "image/jpeg", data: image } }],
      null, [], 1500, { responseMimeType: "application/json", responseSchema: SCHEMA },
    );
    return firstJson(text);
  } catch (e) {
    errors.push(String(e).slice(0, 200));
  }
  throw new Error(errors.join(" | "));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const token = (req.headers.get("Authorization") ?? "").replace("Bearer ", "");
    const { data: u } = await supabase.auth.getUser(token);
    if (!u?.user) return reply({ error: "unauthorised" }, 401);

    const usage = await supabase.rpc("bump_ai_usage", { uid: u.user.id, k: "analyze" });
    if ((usage.data as number) > DAILY_LIMIT) {
      return reply({ is_study_material: false, reason: "You've reached today's upload limit. Try again tomorrow." });
    }

    const body = await req.json();
    const image = String(body.imageBase64 ?? "");
    const hint = String(body.subjectHint ?? "").slice(0, 60);
    if (!image || image.length > 6_000_000) {
      return reply({ is_study_material: false, reason: "The photo is missing or too large." });
    }

    const out = await analyse(image, hint);
    return reply({
      is_study_material: out.is_study_material === true,
      reason: String(out.reason ?? ""),
      readable: out.readable !== false,
      subject: String(out.subject ?? hint ?? "Other"),
      chapter_or_topic: String(out.chapter_or_topic ?? ""),
      topics: Array.isArray(out.topics) ? out.topics.slice(0, 6).map((t: unknown) => String(t)) : [],
      summary: String(out.summary ?? ""),
      difficulty: ["easy", "medium", "hard"].includes(out.difficulty) ? out.difficulty : "medium",
      estimated_study_minutes: Math.min(90, Math.max(10, Number(out.estimated_study_minutes ?? 30))),
      has_questions: out.has_questions === true,
    });
  } catch (e) {
    console.error(String(e));
    return reply({ error: "analysis_failed", detail: String(e).slice(0, 300) }, 500);
  }
});
