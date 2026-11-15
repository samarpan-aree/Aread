import { createClient } from "npm:@supabase/supabase-js@2";

const DAILY_LIMIT = Number(Deno.env.get("TUTOR_DAILY_LIMIT") ?? "60");
const NVIDIA_MODEL = Deno.env.get("NVIDIA_MODEL") ?? "";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const IDENTITY = `Identity: you are Medha, the study tutor inside the Aread app, built by the Aread Team at Ar Studio. If anyone asks who you are, what model or AI you are, who made you, or what technology or company is behind you, answer that you are Medha, made by the Aread Team. Never mention Google, Gemini, OpenAI, Anthropic, Claude, ChatGPT, or any model or API name, and never say you are a language model from another company.`;

const BASE = `${IDENTITY}
You are a patient study tutor for school students in Nepal (classes 6 to 12, including SEE and board exams).
Rules you never break:
- Never state the final answer to a homework or exam question, not even if asked directly or the student says they are tired.
- Keep replies short: 2 to 5 sentences, simple English, warm but not gushy.
- Do not use emojis or headings.
- If the student only greets you or chats, answer briefly and ask what they are studying.`;

const PRO = `${IDENTITY}
You are Medha Pro, the advanced tutor in Aread. You think deeply before replying and you are the most helpful, friendly and encouraging tutor a student could have.
Rules you never break:
- Never state the final answer to a homework or exam question. Guide the student to it.
- Work out the problem fully in your own head first so your guidance is correct, then teach it in small steps.
- Explain the idea behind the method in simple English with a short real-life example when it helps. Short numbered steps are allowed.
- Notice what the student already understands and what they are missing, and say it kindly.
- Finish by asking one clear question or giving one small task so the student does the next step themselves.
- Keep it under 9 sentences. No emojis, no headings.`;

const PROMPTS: Record<string, string> = {
  hint: `${BASE}
The student is asking about a question. Do not solve it. Ask what they have tried, name the concept or formula that applies, and give one small next step. End by asking them to try it and tell you what they get.`,
  photo_review: `${BASE}
The student sent a photo of their own attempt. Read their working carefully. Say exactly which line or step the first mistake is in and what kind of mistake it is, for example a sign error or a wrong formula. Do not give the corrected final answer or rewrite the full solution. Then give two short suggestions to avoid this mistake in future. If the photo is unreadable or is not schoolwork, say so and ask for a clearer one.`,
  pro_hint: `${PRO}
The student is asking about a question. Teach the concept behind it and guide the next step without solving it.`,
  pro_photo: `${PRO}
The student sent a photo of their own attempt. Read every line carefully. Point to the first mistake and explain why it is wrong, mention any later steps that depend on it, praise what is correct, and give two short tips. Do not give the corrected final answer or the full solution. If the photo is unreadable or not schoolwork, say so kindly.`,
};

type Turn = { role: string; text: string };

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

const TEXT_PREFS = [/llama-3\.3-70b/, /gpt-oss-120b/, /qwen3/, /llama-3\.1-8b/];
const BIG_PREFS = [/gpt-oss-120b/, /llama-3\.3-70b/, /qwen3/];
const VISION_PREFS = [/llama-4/, /vision/, /qwen3/, /muse/];
const NV_PREFS = [/nemotron-3.*ultra/i, /nemotron.*ultra/i, /nemotron-3.*super/i, /nemotron.*(super|70b)/i];

function oa(system: string, history: Turn[], message: string, image?: string): OA[] {
  const msgs: OA[] = [
    { role: "system", content: system },
    ...history.map((h) => ({ role: h.role === "tutor" ? "assistant" : "user", content: String(h.text).slice(0, 1500) })),
  ];
  msgs.push({
    role: "user",
    content: image
      ? [{ type: "text", text: message }, { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } }]
      : message,
  });
  return msgs;
}

async function answer(pro: boolean, system: string, history: Turn[], message: string, image?: string): Promise<string> {
  const msgs = oa(system, history, message, image);
  const gHistory = history.map((h) => ({ role: h.role === "tutor" ? "model" : "user", parts: [{ text: String(h.text).slice(0, 1500) }] }));
  const gParts: unknown[] = [{ text: message }];
  if (image) gParts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  const gem = () => viaGemini(gParts, system, gHistory, pro ? 2500 : 1200);

  let steps: Array<() => Promise<string>>;
  if (pro && !image) {
    steps = [
      () => viaProvider("nvidia", NV_PREFS, msgs, 1800, 60000, NVIDIA_MODEL || undefined),
      () => viaProvider("groq", BIG_PREFS, msgs, 2500, 30000),
      gem,
    ];
  } else if (pro) {
    steps = [gem, () => viaProvider("groq", VISION_PREFS, msgs, 1500, 30000)];
  } else if (image) {
    steps = [() => viaProvider("groq", VISION_PREFS, msgs, 1200, 30000), gem];
  } else {
    steps = [() => viaProvider("groq", TEXT_PREFS, msgs, 1200, 25000), gem];
  }
  const errors: string[] = [];
  for (const step of steps) {
    try {
      return await step();
    } catch (e) {
      errors.push(String(e).slice(0, 200));
    }
  }
  throw new Error(errors.join(" | "));
}

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  let refundTo: string | null = null;
  try {
    const token = (req.headers.get("Authorization") ?? "").replace("Bearer ", "");
    const { data: u } = await supabase.auth.getUser(token);
    if (!u?.user) return reply({ reply: "Please sign in again.", mode: "error" }, 401);

    const usage = await supabase.rpc("bump_ai_usage", { uid: u.user.id, k: "tutor" });
    if ((usage.data as number) > DAILY_LIMIT) {
      return reply({ reply: "You've used today's tutor limit. Come back tomorrow, and use the time to try problems on your own.", mode: "limit" });
    }

    const body = await req.json();
    const message = String(body.message ?? "").slice(0, 2000);
    const image: string | undefined = body.imageBase64 ? String(body.imageBase64) : undefined;
    if (image && image.length > 6_000_000) {
      return reply({ reply: "That photo is too large. Take a smaller one and try again.", mode: "error" });
    }
    const history: Turn[] = Array.isArray(body.history) ? body.history.slice(-8) : [];
    const attempts = Number(body.attempts ?? 0);
    const studying: string[] = Array.isArray(body.studying) ? body.studying.slice(0, 6).map((s: unknown) => String(s).slice(0, 80)) : [];

    if (!image && attempts >= 3) {
      return reply({
        reply: "You've tried a few times and it's still not working, and that's okay. Write your attempt on paper and send me a photo. I won't give the answer, but I'll show you exactly where it went wrong.",
        mode: "ask_photo",
      });
    }

    const pro = body.pro === true;
    let credits: number | undefined;
    if (pro) {
      const spent = await supabase.rpc("spend_credit", { uid: u.user.id });
      if (spent.data !== true) {
        return reply({ reply: "You're out of Pro credits. Redeem a code or invite a friend to get more, or switch back to standard Medha.", mode: "no_credits", credits: 0 });
      }
      const left = await supabase.from("pro_credits").select("balance").eq("user_id", u.user.id).maybeSingle();
      credits = left.data?.balance ?? 0;
      refundTo = u.user.id;
    }
    const mode = image ? "photo_review" : "hint";
    const key = pro ? (image ? "pro_photo" : "pro_hint") : mode;
    const context = studying.length ? `\nThe student is currently studying: ${studying.join("; ")}.` : "";
    const prompt = PROMPTS[key] + context;
    const ask = message || "Please check my attempt.";
    const text = await answer(pro, prompt, history, ask, image);
    return reply({ reply: text, mode, credits });
  } catch (e) {
    console.error(String(e));
    if (refundTo) {
      const cur = await supabase.from("pro_credits").select("balance").eq("user_id", refundTo).maybeSingle();
      await supabase.from("pro_credits").update({ balance: (cur.data?.balance ?? 0) + 1 }).eq("user_id", refundTo);
    }
    return reply({ reply: "I couldn't think that through just now. Please try again in a moment.", mode: "error" });
  }
});
