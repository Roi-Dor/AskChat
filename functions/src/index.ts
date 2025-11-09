// redeploy: 2025-11-08 13:29:31
// redeploy: 2025-11-08 13:24:09
import * as admin from "firebase-admin";
import * as functions from "firebase-functions";

admin.initializeApp();
const db = admin.firestore();

const REGION = "europe-west1";
const ASKCHAT_SENDER_ID = "AskChat";

// ---- config (prefer functions:config, then env) ----
type AskchatCfg = {url?: string; token?: string};
type FullCfg = {askchat?: AskchatCfg};

const cfg = (functions.config() as FullCfg) || {};
const BACKEND_URL = cfg.askchat?.url ??
  process.env.ASKCHAT_BACKEND_URL ?? "";
const BACKEND_TOKEN = cfg.askchat?.token ??
  process.env.ASKCHAT_BACKEND_TOKEN ?? "";

console.log(
  "[AskChat] Config",
  JSON.stringify({
    REGION: REGION,
    BACKEND_URL: BACKEND_URL || "(missing)",
  })
);

// ---- types ----
type Msg = {text?: string; senderId?: string; aiHandled?: boolean};
type Chat = {type?: string};
type Source = {chatId: string; messageId: string; text?: string};

/**
 * Call the FastAPI /ask endpoint and return answer + sources.
 * @param {string} question The user's question text.
 * @return {Promise<{answer: string, sources: Source[]}>} Ask response.
 */
async function callAsk(
  question: string
): Promise<{answer: string; sources: Source[]}> {
  if (!BACKEND_URL) throw new Error("BACKEND_URL missing");
  const base = BACKEND_URL.replace(/\/+$/, "");
  const url = base + "/ask";

  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Askchat-Token": BACKEND_TOKEN,
    },
    body: JSON.stringify({question: question, top_k: 5}),
  });

  if (!resp.ok) {
    const body = await resp.text().catch(() => "");
    const msg = "AskChat /ask failed " +
      String(resp.status) + ": " + (body || "(no body)");
    throw new Error(msg);
  }
  return (await resp.json()) as {answer: string; sources: Source[]};
}

// ---- trigger on new message in AskChat room ----
export const onAskChatMessage = functions
  .region(REGION)
  .runWith({timeoutSeconds: 60, memory: "256MB"})
  .firestore.document("chats/{chatId}/messages/{messageId}")
  .onCreate(async (snap, ctx) => {
    const chatId = ctx.params.chatId;
    const messageId = ctx.params.messageId;
    const msg = snap.data() as Msg;

    console.log("[AskChat] onCreate", chatId + "/" + messageId);

    const chatSnap = await db.collection("chats").doc(chatId).get();
    const chat = (chatSnap.data() as Chat | undefined) || {};
    if (chat.type !== "askchat") {
      console.log("[AskChat] Skip: chat.type=" + chat.type);
      return;
    }
    if (!msg.text) {
      console.log("[AskChat] Skip: empty text");
      return;
    }
    if ((msg.senderId || "") === ASKCHAT_SENDER_ID) {
      console.log("[AskChat] Skip: sender is AskChat");
      return;
    }

    // idempotency
    try {
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(snap.ref);
        const cur = fresh.data() as Msg | undefined;
        if (!cur) return;
        if (cur.aiHandled) {
          console.log("[AskChat] Skip: already aiHandled");
          return;
        }
        tx.update(snap.ref, {aiHandled: true});
      });
    } catch (e) {
      console.warn("[AskChat] aiHandled txn error:",
        (e as Error).message);
    }

    // call backend and write reply
    try {
      console.log("[AskChat] Calling /ask ...");
      const data = await callAsk(msg.text as string);
      const sources = (data.sources || [])
        .map((s) => s.chatId + "::" + s.messageId);

      await db.collection("chats").doc(chatId)
        .collection("messages").add({
          text: data.answer ||
            "Sorry, I couldn’t find anything relevant.",
          senderId: ASKCHAT_SENDER_ID,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          sources: sources,
        });
      console.log("[AskChat] Reply written.");
    } catch (e) {
      console.error("[AskChat] /ask error:", (e as Error).message);
      await db.collection("chats").doc(chatId)
        .collection("messages").add({
          text: "Sorry, I couldn’t reach AskChat’s brain. " +
            "Try again later.",
          senderId: ASKCHAT_SENDER_ID,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
        });
    }
  });
