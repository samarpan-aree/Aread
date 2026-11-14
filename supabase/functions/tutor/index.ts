import { createClient } from "npm:@supabase/supabase-js@2";

const GEMINI_KEY = Deno.env.get("GEMINI_API_KEY") ?? "";
const MODELS = [Deno.env.get("GEMINI_MODEL") ?? "gemini-3.8-flash", "gemini-flash-latest"];
const PRO_MODELS = [Deno.env.get("PRO_MODEL") ?? "gemini-pro-latest", "gemini-3.8-flash", "gemini-flash-latest"];
const DAILY_LIMIT = Number(Deno.env.get("TUTOR_DAILY_LIMIT") ?? "60");

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

async function gemini(models: string[], budget: number, system: string, history: Turn[], message: string, image?: string): Promise<string> {
  const contents = history.map((h) => ({
    role: h.role === "tutor" ? "model" : "user",
    parts: [{ text: String(h.text).slice(0, 1500) }],
  }));
  const parts: unknown[] = [{ text: message }];
  if (image) parts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  contents.push({ role: "user", parts });

  let lastError = "no model";
  for (const model of models) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_KEY },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: system }] },
        contents,
        generationConfig: { temperature: 0.5, maxOutputTokens: budget },
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
    const text = await gemini(pro ? PRO_MODELS : MODELS, pro ? 3000 : 1500, PROMPTS[key] + context, history, message || "Please check my attempt.", image);
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
