// v2 HTTPS + Firestore, v1 Auth
import {onRequest} from "firebase-functions/v2/https";
import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {setGlobalOptions} from "firebase-functions/v2/options";
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

setGlobalOptions({region: "us-east4"});

admin.initializeApp();
const db = admin.firestore();
const ASKCHAT_UID = "askchat";

// 1) Ensure AskChat user exists (trigger once via browser/console)
export const ensureAskChatUser = onRequest(async (_req, res) => {
  const doc = await db.collection("users").doc(ASKCHAT_UID).get();
  if (!doc.exists) {
    await db.collection("users").doc(ASKCHAT_UID).set({
      displayName: "AskChat",
      photoUrl: null,
      fcmTokens: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  }
  res.status(200).send("ok");
});

// 2) On Auth user create (v1) -> create system chat with AskChat
export const userCreatedCreateAskChat = functions
  .region("us-east4")
  .auth.user()
  .onCreate(async (user) => {
    const participants = [user.uid, ASKCHAT_UID].sort();
    const chatId = participants.join("_");
    const chatRef = db.collection("chats").doc(chatId);

    if ((await chatRef.get()).exists) return;

    await chatRef.set({
      type: "system",
      participants,
      lastMessage: {
        text: "Hi! I’m AskChat. Ask me anything about your chats.",
        senderId: ASKCHAT_UID,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      },
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await chatRef.collection("messages").add({
      senderId: ASKCHAT_UID,
      text: "Hi! I’m AskChat. Ask me anything about your chats.",
      mediaUrl: null,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

// 3) On message create (v2) -> update chat summary + send FCM
export const messageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event: any) => {
    const snap = event.data;
    if (!snap) return;

    const chatId = event.params.chatId;
    const message = snap.data() as {
      senderId: string;
      text?: string|null;
      mediaUrl?: string|null;
    };

    const chatRef = db.collection("chats").doc(chatId);
    const chatDoc = await chatRef.get();
    const chat = chatDoc.data() as {participants: string[]}|undefined;
    if (!chat) return;

    // Update chat summary
    await chatRef.update({
      lastMessage: {
        text: message.text ?? (message.mediaUrl ? "Media" : ""),
        senderId: message.senderId,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      },
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Push notify recipients
    const recipients = chat.participants.filter((p) => p !== message.senderId);
    for (const uid of recipients) {
      const userDoc = await db.collection("users").doc(uid).get();
      const tokens = (userDoc.data()?.fcmTokens as string[]) ?? [];
      if (!tokens.length) continue;

      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: {
          title: "New message",
          body: message.text ?? "Sent you a photo",
        },
        data: {chatId},
      });
    }
  }
);

