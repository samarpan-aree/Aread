import { createClient } from "npm:@supabase/supabase-js@2";

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const MODELS = [Deno.env.get("GEMINI_MODEL") ?? "gemini-flash-latest", "gemini-2.5-flash"];
const DAILY_LIMIT = Number(Deno.env.get("TUTOR_DAILY_LIMIT") ?? "60");

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const BASE = `You are Medha, a patient study tutor for school students in Nepal (classes 6 to 12, including SEE and board exams).
Rules you never break:
- Never state the final answer to a homework or exam question, not even if asked directly or the student says they are tired.
- Keep replies short: 2 to 5 sentences, simple English, warm but not gushy.
- Do not use emojis, headings, or bullet lists.
- If the student only greets you or chats, answer briefly and ask what they are studying.`;

const PROMPTS: Record<string, string> = {
  hint: `${BASE}
The student is asking about a question. Do not solve it. Ask what they have tried, name the concept or formula that applies, and give one small next step. End by asking them to try it and tell you what they get.`,
  photo_review: `${BASE}
The student sent a photo of their own attempt. Read their working carefully. Say exactly which line or step the first mistake is in and what kind of mistake it is, for example a sign error or a wrong formula. Do not give the corrected final answer or rewrite the full solution. Then give two short suggestions to avoid this mistake in future. If the photo is unreadable or is not schoolwork, say so and ask for a clearer one.`,
};

type Turn = { role: string; text: string };

async function gemini(system: string, history: Turn[], message: string, image?: string): Promise<string> {
  const contents = history.map((h) => ({
    role: h.role === "tutor" ? "model" : "user",
    parts: [{ text: String(h.text).slice(0, 1500) }],
  }));
  const parts: unknown[] = [{ text: message }];
  if (image) parts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  contents.push({ role: "user", parts });

  let lastError = "no model";
  for (const model of MODELS) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_KEY },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: system }] },
        contents,
        generationConfig: { temperature: 0.5, maxOutputTokens: 500 },
      }),
    });
    if (!r.ok) {
      lastError = `${model} ${r.status} ${(await r.text()).slice(0, 200)}`;
      continue;
    }
    const j = await r.json();
    const text = (j.candidates?.[0]?.content?.parts ?? [])
      .map((p: { text?: string }) => p.text ?? "")
      .join("")
      .trim();
    if (text) return text;
    lastError = `${model} empty`;
  }
  throw new Error(lastError);
}

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
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

    const mode = image ? "photo_review" : "hint";
    const context = studying.length ? `\nThe student is currently studying: ${studying.join("; ")}.` : "";
    const text = await gemini(PROMPTS[mode] + context, history, message || "Please check my attempt.", image);
    return reply({ reply: text, mode });
  } catch (e) {
    console.error(String(e));
    return reply({ reply: "I couldn't think that through just now. Please try again in a moment.", mode: "error" });
  }
});
