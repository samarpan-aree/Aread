# Aread

Android study app: focus sessions, app and call blocking, an AI tutor named Medha that guides without giving answers, streaks and a weekly leaderboard.

Stack: Kotlin + Jetpack Compose (native Android), Supabase (auth, Postgres, storage, edge functions). Free tier is enough to start.

## What you need to do

### 1. Supabase (10 minutes)
1. Create a project at supabase.com.
2. SQL Editor: paste and run `supabase/migrations/001_init.sql`.
3. Authentication > Providers: turn on Email, Phone (needs an SMS provider such as Twilio), Google, Facebook. Turn **off** "Allow anonymous sign-ins" so there is no guest mode.
4. Authentication > URL Configuration: add redirect URL `aread://auth`.
5. Settings > API: copy the Project URL and the `anon` key.

### 2. Hide the AI key (never put it in the app)
Install the Supabase CLI, then:

```
supabase link --project-ref <your-ref>
supabase secrets set GEMINI_API_KEY=<your key from aistudio.google.com>
supabase secrets set GROQ_API_KEY=<optional fallback key from console.groq.com>
supabase functions deploy tutor
```

Models are set by env (`GEMINI_MODEL`, `GROQ_TEXT_MODEL`, `GROQ_VISION_MODEL`) so you can switch to a newer one without changing the app. Check the current model names on each provider's site; free-tier limits and names change. Gemini is the primary (it reads photos of handwritten work), Groq is the automatic fallback. Each student is limited to 60 tutor messages a day (`TUTOR_DAILY_LIMIT`).

### 3. Google and Facebook login
- Google: in Google Cloud Console create an OAuth **Web** client ID and an **Android** client (package `com.aread.app` plus your SHA-1). Put the Web client ID in `secrets.properties` and in Supabase's Google provider.
- Facebook: create an app at developers.facebook.com and paste its ID and secret into Supabase's Facebook provider.

### 4. Configure the app
```
cp secrets.properties.example secrets.properties
# fill SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_WEB_CLIENT_ID
```
Open the folder in Android Studio and run on a real phone (blocking and call screening do not work well on emulators).

### 5. Assets I could not create (the app runs without the avatar file; that area just stays empty)
Put these in `app/src/main/assets/`:
- `medha.glb`: a rigged head or bust with ARKit-style blendshapes (`jawOpen`, `mouthFunnel`, `mouthPucker`, `mouthSmileLeft/Right`, `mouthClose`, `eyeBlinkLeft/Right`). A VRM or a model made in Blender or Avaturn exported as GLB works.

Also add launcher icons (Android Studio: New > Image Asset).

### 6. Make yourself admin
Sign in once, then in the SQL editor:
```
update public.profiles set is_admin = true where id = '<your user uuid from Authentication > Users>';
```
Hidden entry: Settings > tap the "Aread 1.0.0" line 7 times. The server checks `is_admin` on every admin call, so the tap pattern is only a door, not the lock.

## How the rules work
- Tutor: attempts 0-2 give hints only. From the 3rd attempt Medha asks for a photo of the student's work. With a photo, Medha points out only where the mistake is and gives suggestions. The "never give the answer" rule is in the server prompt, so it can't be removed from the app.
- Inactivity: a background job checks every 3 hours. After 2 days without opening Aread it sends a notification and pauses blocked apps for 60 minutes.
- Streaks, weekly minutes and the leaderboard are computed in the database (`log_focus_session`), so students can't edit them.

## Known limits
- Not compiled yet: this was written without the Android SDK. Expect a few small fixes on first build, most likely in the SceneView avatar code (`AvatarView.kt`, one helper function) and supabase-kt calls if library versions moved.
- The Accessibility-based app blocker is the standard way to do this on Android, but Google Play reviews it closely. Prepare a short demo video and the privacy policy text for the submission.
- The intro is a Compose text animation; a video can replace it later (Media3 is already included).
- Lip sync is approximate and driven by the spoken letters from the phone's built-in voice. Much more accurate sync needs a server TTS that returns viseme timings; the `Mouth` interface is where that would plug in.
- Auto study-time suggestions use simple rules (`StudyPlanner.kt`), not yet the uploaded textbook content.
