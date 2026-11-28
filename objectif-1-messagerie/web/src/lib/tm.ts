// Chargement du core Kotlin/Wasm (TModeler).
// Construit par `npm run core:build` puis copié par `npm run sync:core` dans src/core.
// Sans lui, l'interface fonctionne avec les mocks.
import { DB_NAME } from './config'

export type NGomaCore = {
  ngomaInit(dbName: string): boolean

  // ── Module utilisateurs ──────────────────────────────────────────────────
  ngomaRegister(name: string, identifier: string, passwordHash: string): string
  ngomaLogin(identifier: string, passwordHash: string): string
  ngomaRestoreSession(): string
  ngomaLogout(): string
  ngomaUsers(query: string): string
  ngomaSearchUsers(query: string, currentUserId: string): string
  ngomaGetUser(userId: string): string
  ngomaUpdateProfile(userId: string, newName: string, newBio: string): string
  ngomaDeleteAccount(userId: string): string

  // ── Module conversations ─────────────────────────────────────────────────
  ngomaConversations(userId: string, showArchived: boolean): string
  ngomaCreateConversation(creatorId: string, otherIdentifier: string): string
  ngomaSetPinned(conversationId: string, userId: string, value: boolean): string
  ngomaSetMuted(conversationId: string, userId: string, value: boolean): string
  ngomaSetArchived(conversationId: string, userId: string, value: boolean): string
  ngomaMarkRead(conversationId: string, userId: string): string
  ngomaMessages(conversationId: string): string
  ngomaSend(conversationId: string, authorId: string, text: string, replyToId: string, attachment: string): string
  ngomaEditMessage(messageId: string, authorId: string, newText: string): string
  ngomaDeleteMessage(messageId: string, authorId: string): string
  ngomaDeleteConversation(conversationId: string, userId: string): string
  ngomaReact(messageId: string, userId: string, emoji: string): string

  // ── Module publications ──────────────────────────────────────────────────
  ngomaPosts(): string
  ngomaUserPosts(authorId: string): string
  ngomaCreatePost(authorId: string, caption: string, mediaType: string, mediaData: string, sizeBytes: string): string
  ngomaMediaData(mediaId: string): string
  ngomaEditPost(postId: string, authorId: string, newCaption: string): string
  ngomaDeletePost(postId: string, authorId: string): string
  ngomaToggleLike(postId: string, userId: string): string
  ngomaComments(postId: string): string
  ngomaAddComment(postId: string, authorId: string, text: string, parentId: string): string
  ngomaEditComment(commentId: string, authorId: string, text: string): string
  ngomaRemoveComment(commentId: string, authorId: string): string
  ngomaToggleCommentLike(commentId: string, userId: string): string
}

let core: NGomaCore | null = null

export async function startTModeler(): Promise<boolean> {
  try {
    const load = Object.values(
      import.meta.glob<{ default?: unknown }>('../core/ngoma-core-web-wasm-js.mjs')
    )[0]
    if (!load) return false
    const mod = await load()
    const c   = (mod.default ?? mod) as NGomaCore
    if (!c.ngomaInit(DB_NAME)) return false
    core = c
    return true
  } catch {
    return false
  }
}

export function getCore(): NGomaCore | null {
  return core
}
