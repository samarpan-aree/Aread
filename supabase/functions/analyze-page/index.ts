import { createClient } from "npm:@supabase/supabase-js@2";

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const MODELS = [Deno.env.get("GEMINI_MODEL") ?? "gemini-3.8-flash", "gemini-flash-latest"];
const GROQ_KEY = Deno.env.get("GROQ_API_KEY") ?? "";
const GROQ_MODELS = [Deno.env.get("GROQ_VISION_MODEL") ?? "meta-llama/llama-4-scout-17b-16e-instruct"];
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

async function groqAnalyse(image: string, hint: string) {
  let lastError = "groq failed";
  for (const model of GROQ_MODELS) {
    const r = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${GROQ_KEY}` },
      body: JSON.stringify({
        model,
        temperature: 0.2,
        max_tokens: 900,
        response_format: { type: "json_object" },
        messages: [{
          role: "user",
          content: [
            { type: "text", text: `${PROMPT}\n${hint ? `The student says the subject is: ${hint}.\n` : ""}${JSON_SHAPE}` },
            { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } },
          ],
        }],
      }),
    });
    if (!r.ok) {
      lastError = `groq ${model} ${r.status} ${(await r.text()).slice(0, 200)}`;
      continue;
    }
    const j = await r.json();
    try {
      return JSON.parse(j.choices?.[0]?.message?.content ?? "");
    } catch {
      lastError = `groq ${model} bad json`;
    }
  }
  throw new Error(lastError);
}

async function analyse(image: string, hint: string) {
  if (GROQ_KEY) {
    try {
      return await groqAnalyse(image, hint);
    } catch (e) {
      console.error(String(e));
    }
  }
  let lastError = "no model";
  for (const model of MODELS) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_KEY },
      body: JSON.stringify({
        contents: [{
          role: "user",
          parts: [
            { text: hint ? `${PROMPT}\nThe student says the subject is: ${hint}.` : PROMPT },
            { inline_data: { mime_type: "image/jpeg", data: image } },
          ],
        }],
        generationConfig: { temperature: 0.2, maxOutputTokens: 2000, responseMimeType: "application/json", responseSchema: SCHEMA },
      }),
    });
    if (!r.ok) {
      lastError = `${model} ${r.status} ${(await r.text()).slice(0, 200)}`;
      continue;
    }
    const j = await r.json();
    const text = (j.candidates?.[0]?.content?.parts ?? []).map((p: { text?: string }) => p.text ?? "").join("");
    try {
      return JSON.parse(text);
    } catch {
      lastError = `${model} bad json`;
    }
  }
  throw new Error(lastError);
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
    return reply({ error: "analysis_failed" }, 500);
  }
});
