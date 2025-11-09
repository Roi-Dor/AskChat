import * as admin from "firebase-admin";
import axios from "axios";
import * as dotenv from "dotenv";
dotenv.config();

admin.initializeApp({
  credential: admin.credential.cert(require("../service-account.json")),
  projectId: process.env.GOOGLE_CLOUD_PROJECT || "askchat-e7148",
});
const db = admin.firestore();

const AI_BACKEND_URL =
  process.env.AI_BACKEND_URL || "http://127.0.0.1:8000";

type MsgTS =
  | FirebaseFirestore.Timestamp
  | {seconds: number; nanoseconds?: number}
  | number
  | undefined;

interface MsgDoc {
  text?: string;
  senderId?: string;
  type?: string;
  createdAt?: MsgTS;
}

function toEpochMs(v: MsgTS): number | undefined {
  type TS = {toMillis?: () => number; seconds?: number};
  const x = v as TS | number | undefined;
  if (x == null) return undefined;
  if (typeof (x as TS).toMillis === "function") {
    return (x as Required<TS>).toMillis();
  }
  if (typeof (x as TS).seconds === "number") {
    return ((x as TS).seconds as number) * 1000;
  }
  if (typeof x === "number") return x;
  return undefined;
}

async function* iterAllMessages() {
  const chatsSnap = await db.collection("chats").get();
  for (const chatDoc of chatsSnap.docs) {
    const chatId = chatDoc.id;
    const msgs = await chatDoc.ref
      .collection("messages")
      .orderBy("timestamp") // adjust if your field is named createdAt
      .get();
    for (const m of msgs.docs) {
      yield {chatId, messageId: m.id, data: m.data() as MsgDoc};
    }
  }
}

(async () => {
  let count = 0, sent = 0, skipped = 0, failed = 0;

  for await (const {chatId, messageId, data} of iterAllMessages()) {
    count++;
    const text = (data.text ?? "").toString().trim();
    const senderId = (data.senderId ?? "").toString();
    const timestamp = toEpochMs(data.createdAt) ?? Date.now();

    if (!text || data.type === "system") {
      skipped++;
      continue;
    }

    const payload = {chatId, messageId, text, senderId, timestamp};

    try {
      await axios.post(`${AI_BACKEND_URL}/embed-message`, payload, {
        timeout: 20000,
        // headers: {"X-AskChat-Token": process.env.ASKCHAT_BACKEND_TOKEN ?? ""},
      });
      sent++;

      // polite pacing if you have lots of data
      if (sent % 100 === 0) {
        console.log("progress", {count, sent, skipped, failed});
        await new Promise((r) => setTimeout(r, 200)); // short pause
      }
    } catch (err) {
      const isAxios =
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (err as any)?.isAxiosError === true ||
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (typeof (err as any)?.toJSON === "function" && (err as any)?.name === "AxiosError");

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const status = isAxios ? (err as any)?.response?.status : undefined;
      const msg =
        isAxios ? (err as Error).message :
        (err as Error)?.message ?? String(err);

      console.error("backfill fail", chatId, messageId, status ?? "no-status", msg);
      failed++;
    }
  }

  console.log({count, sent, skipped, failed});
  process.exit(0);
})();
