import { AccountBridgeMock, MessagingBridgeMock, VideoBridgeMock } from './mock'
import type { AccountBridge, MessagingBridge, VideoBridge, User, Conversation, Message } from './types'
import { getCore, type NGomaCore } from './tm'

// =============================================================================
// Helpers
// =============================================================================

/** Les emojis UI restent tels quels — le core accepte directement les emojis Unicode. */
function parse<T>(json: string): T {
  const obj = JSON.parse(json) as { error?: string } & T
  if (obj.error) throw new Error(obj.error)
  return obj
}

function parseSoft<T>(json: string): T | null {
  try {
    const obj = JSON.parse(json) as { error?: string } & T
    if (obj.error) return null
    return obj
  } catch { return null }
}

function hashPassword(password: string): string {
  return btoa(password)
}

function parseDate(dateStr: string): number {
  if (!dateStr) return Date.now()
  try { return new Date(dateStr.replace('T', ' ')).getTime() || Date.now() }
  catch { return Date.now() }
}

/** reactions du core : {"👍":2,"❤️":1,...} → Record<emoji, number> */
function parseReactions(raw: string): Record<string, number> {
  if (!raw || raw === '{}') return {}
  try {
    const parsed = JSON.parse(raw) as Record<string, number>
    const result: Record<string, number> = {}
    for (const [k, v] of Object.entries(parsed)) {
      if (v > 0) result[k] = v
    }
    return result
  } catch { return {} }
}

// =============================================================================
// AccountBridgeCore
// =============================================================================

class AccountBridgeCore implements AccountBridge {

  async restoreSession(): Promise<User | null> {
    const c = getCore()
    if (!c) return null
    const result = parseSoft<{ id: string; name: string }>(c.ngomaRestoreSession())
    if (!result) return null
    return { id: Number(result.id), name: result.name }
  }

  async login(identifier: string, password: string): Promise<User> {
    const c = getCore()
    if (!c) return new AccountBridgeMock().login(identifier, password)
    const result = parse<{ id: string; name: string }>(
      c.ngomaLogin(identifier, hashPassword(password))
    )
    return { id: Number(result.id), name: result.name }
  }

  async register(name: string, identifier: string, password: string): Promise<User> {
    const c = getCore()
    if (!c) return new AccountBridgeMock().register(name, identifier, password)
    const result = parse<{ id: string; name: string }>(
      c.ngomaRegister(name, identifier, hashPassword(password))
    )
    return { id: Number(result.id), name: result.name }
  }

  async logout(): Promise<void> {
    const c = getCore()
    if (c) c.ngomaLogout()
  }

  // Gestion des utilisateurs (M1 SI, module 1) : ?crans livr?s par Foundation avec le mock du mod?le.
  // ? brancher sur votre core : ngomaSearchUsers, ngomaGetUser, ngomaUpdateProfile, ngomaSetActive.
  private readonly usersMock = new AccountBridgeMock()
  subscribe(listener: () => void): () => void { return this.usersMock.subscribe(listener) }
  users(query?: string): User[] { return this.usersMock.users(query) }
  user(id: number): User | undefined { return this.usersMock.user(id) }
  updateProfile(patch: { name?: string; bio?: string }): void { this.usersMock.updateProfile(patch) }
  deleteAccount(): Promise<void> { return this.usersMock.deleteAccount() }
}

// =============================================================================
// MessagingBridgeCore
// =============================================================================

class MessagingBridgeCore implements MessagingBridge {

  private listeners  = new Set<() => void>()
  private currentUserId: string
  private _conversations: Conversation[] = []
  private _messages: Map<number, Message[]> = new Map()
  private _pollTimer: ReturnType<typeof setInterval> | null = null

  constructor(userId: string) {
    this.currentUserId = userId
    this.reload()
    // Polling 2 s — simule les notifications en mode offline
    this._pollTimer = setInterval(() => {
      this.reload()
      this.emit()
    }, 2000)
  }

  // ---------------------------------------------------------------------------
  // Abonnement
  // ---------------------------------------------------------------------------

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
      if (this.listeners.size === 0 && this._pollTimer !== null) {
        clearInterval(this._pollTimer)
        this._pollTimer = null
      }
    }
  }

  private emit(): void { this.listeners.forEach((l) => l()) }

  private reload(): void {
    const c = getCore()
    if (!c) return
    const rawConvs = parseSoft<unknown[]>(c.ngomaConversations(this.currentUserId, true))
    this._conversations = (rawConvs ?? []).map((r: any) => ({
      id:       Number(r.id),
      title:    r.titre as string,
      unread:   Number(r.unread),
      pinned:   Boolean(r.pinned),
      muted:    Boolean(r.muted),
      archived: Boolean(r.archived),
    }))
    for (const conv of this._conversations) {
      this._loadMessages(String(conv.id))
    }
  }

  private _loadMessages(conversationId: string): void {
    const c = getCore()
    if (!c) return
    const raw = parseSoft<unknown[]>(c.ngomaMessages(conversationId))
    const cid = Number(conversationId)
    this._messages.set(
      cid,
      (raw ?? []).map((r: any) => ({
        id:             Number(r.id),
        conversationId: cid,
        authorId:       Number(r.authorId),
        text:           r.text as string,
        time:           parseDate(r.sentAt as string),
        replyToId:      r.replyToId !== '0' ? Number(r.replyToId) : undefined,
        attachment:     r.attachmentId ? (r.attachmentId as string) : undefined,
        edited:         Boolean(r.edited),
        deleted:        Boolean(r.deleted),
        deletedForMe:   (r.deletedForIds as string ?? '').split(',').includes(this.currentUserId),
        reactions:      parseReactions(r.reactions as string),
      }))
    )
  }

  // ---------------------------------------------------------------------------
  // Lecture
  // ---------------------------------------------------------------------------

  conversations(): Conversation[] { return this._conversations }

  messages(conversationId: number): Message[] {
    return this._messages.get(conversationId) ?? []
  }

  lastMessage(conversationId: number): Message | undefined {
    return [...this.messages(conversationId)].reverse().find((m) => !m.deleted)
  }

  // ---------------------------------------------------------------------------
  // Écriture
  // ---------------------------------------------------------------------------

  send(conversationId: number, text: string, opts?: { replyToId?: number; attachment?: string }): void {
    const c = getCore()
    if (!c) return
    c.ngomaSend(
      String(conversationId),
      this.currentUserId,
      text,
      String(opts?.replyToId ?? 0),
    )
    this._loadMessages(String(conversationId))
    this.emit()
  }

  edit(messageId: number, text: string): void {
    const c = getCore()
    if (!c) return
    c.ngomaEditMessage(String(messageId), this.currentUserId, text)
    this._reloadAllMessages()
    this.emit()
  }

  remove(messageId: number): void {
    const c = getCore()
    if (!c) return
    c.ngomaDeleteMessage(String(messageId), this.currentUserId)
    this._reloadAllMessages()
    this.emit()
  }

  removeForMe(messageId: number): void {
    const c = getCore()
    if (!c) return
    c.ngomaDeleteMessageForMe(String(messageId), this.currentUserId)
    this._reloadAllMessages()
    this.emit()
  }

  react(messageId: number, emoji: string): void {
    const c = getCore()
    if (!c) return
    // Les emojis passent directement (👍 ❤️ 😂 😮) — le core les accepte tels quels
    c.ngomaReact(String(messageId), this.currentUserId, emoji)
    this._reloadAllMessages()
    this.emit()
  }

  markRead(conversationId: number): void {
    const c = getCore()
    if (!c) return
    c.ngomaMarkRead(String(conversationId), this.currentUserId)
    this.reload()
    this.emit()
  }

  setPinned(conversationId: number, value: boolean): void {
    this._patchConv(conversationId, (c) =>
      c.ngomaSetPinned(String(conversationId), this.currentUserId, value))
  }

  setMuted(conversationId: number, value: boolean): void {
    this._patchConv(conversationId, (c) =>
      c.ngomaSetMuted(String(conversationId), this.currentUserId, value))
  }

  setArchived(conversationId: number, value: boolean): void {
    this._patchConv(conversationId, (c) =>
      c.ngomaSetArchived(String(conversationId), this.currentUserId, value))
  }

  /**
   * Crée une conversation.
   * title = numéro de téléphone de l'interlocuteur (RG-C3).
   * RG-C2 : si la conversation existe déjà, retourne son id.
   */
  deleteConversation(conversationId: number): void {
    const c = getCore()
    if (!c) return
    c.ngomaDeleteConversation(String(conversationId), this.currentUserId)
    this.reload()
    this.emit()
  }

  createConversation(title: string): number {
    const c = getCore()
    if (!c) return new MessagingBridgeMock().createConversation(title)

    const raw = c.ngomaCreateConversation(this.currentUserId, title.trim())
    const result = parseSoft<{ id: string; existing: boolean }>(raw)
    if (!result) return -1
    this.reload()
    this.emit()
    return Number(result.id)
  }

  // ---------------------------------------------------------------------------
  // Helpers privés
  // ---------------------------------------------------------------------------

  private _patchConv(conversationId: number, action: (c: NGomaCore) => void): void {
    const c = getCore()
    if (!c) return
    action(c)
    this.reload()
    this.emit()
  }

  private _reloadAllMessages(): void {
    for (const conv of this._conversations) {
      this._loadMessages(String(conv.id))
    }
  }
}

// =============================================================================
// Point de bascule : TModeler si disponible, mock sinon
// =============================================================================

export function makeMessagingBridge(userId: number): MessagingBridge {
  if (getCore()) return new MessagingBridgeCore(String(userId))
  return new MessagingBridgeMock()
}

export const accountBridge: AccountBridge = new AccountBridgeCore()
export const messagingBridge: MessagingBridge = new MessagingBridgeMock()
// Publications (M1 SI, module 3) : ?crans livr?s par Foundation avec le mock du mod?le.
// ? brancher sur votre core : ngomaPosts, ngomaUserPosts, ngomaCreatePost, ngomaLikePost, ngomaComments...
export const videoBridge: VideoBridge = new VideoBridgeMock()

export * from './types'
export { ME } from './mock'
export { makeMessagingBridge as createMessagingBridge }
