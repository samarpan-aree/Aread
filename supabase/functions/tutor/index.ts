// Aread AI tutor proxy. API keys live only in Supabase secrets; the app never sees them.
// Deploy: supabase functions deploy tutor
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY");
const GROQ_KEY = Deno.env.get("GROQ_API_KEY");
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash";
const GROQ_TEXT_MODEL = Deno.env.get("GROQ_TEXT_MODEL") ?? "llama-3.3-70b-versatile";
const GROQ_VISION_MODEL = Deno.env.get("GROQ_VISION_MODEL") ?? "meta-llama/llama-4-scout-17b-16e-instruct";
const DAILY_LIMIT = Number(Deno.env.get("TUTOR_DAILY_LIMIT") ?? "60");

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const BASE = `You are Medha, a patient study tutor for school students in Nepal (classes 6-12, including SEE and board exams).
Rules you never break:
- Never state the final answer to a homework or exam question, not even if asked directly or the student says they are tired.
- Keep replies short: 2-5 sentences, simple English, warm but not gushy.
- Do not use emojis, headings, or bullet lists.`;

const PROMPTS: Record<string, string> = {
  hint: `${BASE}
The student is asking about a question. Do not solve it. Ask what they have tried, name the concept or formula that applies, and give one small next step. End by asking them to try it and tell you what they get.`,
  photo_review: `${BASE}
The student sent a photo of their own attempt. Read their working carefully. Say exactly which line or step the first mistake is in and what kind of mistake it is (for example a sign error or wrong formula). Do not give the corrected final answer or rewrite the full solution. Then give two short suggestions to avoid this mistake in future. If the photo is unreadable, say so and ask for a clearer one.`,
};

function mode(attempts: number, hasImage: boolean): "hint" | "ask_photo" | "photo_review" {
  if (hasImage) return "photo_review";
  return attempts >= 3 ? "ask_photo" : "hint";
}

async function callGemini(system: string, history: { role: string; text: string }[], message: string, image?: string) {
  const contents = history.map((h) => ({ role: h.role === "tutor" ? "model" : "user", parts: [{ text: h.text }] }));
  const parts: unknown[] = [{ text: message }];
  if (image) parts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  contents.push({ role: "user", parts } as never);
  const r = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ system_instruction: { parts: [{ text: system }] }, contents, generationConfig: { temperature: 0.5, maxOutputTokens: 400 } }),
    },
  );
  if (!r.ok) throw new Error(`gemini ${r.status}`);
  const j = await r.json();
  const text = j.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? "").join("").trim();
  if (!text) throw new Error("gemini empty");
  return text as string;
}

async function callGroq(system: string, history: { role: string; text: string }[], message: string, image?: string) {
  const msgs: unknown[] = [{ role: "system", content: system }];
  for (const h of history) msgs.push({ role: h.role === "tutor" ? "assistant" : "user", content: h.text });
  msgs.push({
    role: "user",
    content: image
      ? [{ type: "text", text: message }, { type: "image_url", image_url: { url: `data:image/jpeg;base64,${image}` } }]
      : message,
  });
  const r = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${GROQ_KEY}` },
    body: JSON.stringify({ model: image ? GROQ_VISION_MODEL : GROQ_TEXT_MODEL, messages: msgs, temperature: 0.5, max_tokens: 400 }),
  });
  if (!r.ok) throw new Error(`groq ${r.status}`);
  const j = await r.json();
  return (j.choices?.[0]?.message?.content ?? "").trim() as string;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data: u } = await supabase.auth.getUser(authHeader.replace("Bearer ", ""));
    if (!u?.user) return new Response(JSON.stringify({ error: "unauthorised" }), { status: 401, headers: CORS });

    const calls = await supabase.rpc("bump_tutor_usage", { uid: u.user.id });
    if ((calls.data as number) > DAILY_LIMIT) {
      return Response.json({ reply: "You've used today's tutor limit. Come back tomorrow, and use the time to try problems on your own.", mode: "limit" }, { headers: CORS });
    }

    const body = await req.json();
    const message: string = String(body.message ?? "").slice(0, 2000);
    const image: string | undefined = body.imageBase64 ? String(body.imageBase64) : undefined;
    if (image && image.length > 6_000_000) return Response.json({ reply: "That photo is too large. Take a smaller one and try again.", mode: "error" }, { headers: CORS });
    const history = Array.isArray(body.history) ? body.history.slice(-8) : [];
    const m = mode(Number(body.attempts ?? 0), !!image);

    if (m === "ask_photo") {
      return Response.json({
        reply: "You've tried a few times and it's still not working, and that's okay. Write your attempt on paper and send me a photo. I won't give the answer, but I'll show you exactly where it went wrong.",
        mode: "ask_photo",
      }, { headers: CORS });
    }

    const system = PROMPTS[m];
    let reply = "";
    try {
      if (GEMINI_KEY) reply = await callGemini(system, history, message, image);
      else throw new Error("no gemini key");
    } catch {
      if (!GROQ_KEY) throw new Error("no providers available");
      reply = await callGroq(system, history, message, image);
    }
    return Response.json({ reply, mode: m }, { headers: CORS });
  } catch (e) {
    console.error(e);
    return Response.json({ reply: "I couldn't think that through just now. Please try again in a moment.", mode: "error" }, { status: 200, headers: CORS });
  }
});
