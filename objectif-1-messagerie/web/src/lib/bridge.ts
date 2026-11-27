import { AccountBridgeMock, MessagingBridgeMock, VideoBridgeMock, ME } from './mock'
import type { AccountBridge, MessagingBridge, VideoBridge, User, Conversation, Message, Post, PostComment } from './types'
import { getCore, type NGomaCore } from './tm'

// =============================================================================
// Utilisateur connecté et identifiant « ME » des écrans Foundation
// =============================================================================

/** Identifiant réel (_id TModeler) de l'utilisateur connecté ; null si personne n'est connecté. */
let currentUserId: number | null = null

/**
 * Les écrans Foundation reconnaissent l'utilisateur connecté par la constante ME (= 1).
 * Pour ne pas modifier les écrans, on échange son identifiant réel avec ME dans tout ce que
 * les bridges utilisateurs et publications renvoient (et inversement pour ce qu'ils reçoivent).
 * L'échange est symétrique : la même fonction sert dans les deux sens.
 */
function swapMe(id: number): number {
  if (currentUserId == null || currentUserId === ME) return id
  if (id === currentUserId) return ME
  if (id === ME) return currentUserId
  return id
}

/** Avertit les écrans abonnés et relit l'état toutes les 5 s (simule les notifications sans serveur). */
class Poller {
  private listeners = new Set<() => void>()
  private timer: ReturnType<typeof setInterval> | null = null
  constructor(private readonly onTick: () => void = () => undefined) {}

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    if (!this.timer) this.timer = setInterval(() => { this.onTick(); this.emit() }, 5000)
    return () => {
      this.listeners.delete(listener)
      if (this.listeners.size === 0 && this.timer) { clearInterval(this.timer); this.timer = null }
    }
  }

  emit(): void { this.listeners.forEach((l) => l()) }
}

/** Affiche dans la console une opération refusée par le core et indique si elle a réussi. */
function report(raw: string, action: string): boolean {
  if (raw.includes('"error"')) { console.error(`[NGoma] ${action} refusé :`, raw); return false }
  return true
}

function toUser(r: any): User {
  return {
    id:       swapMe(Number(r.id)),
    name:     r.name as string,
    bio:      (r.bio as string) ?? '',
    joinedAt: r.joinedAt ? parseDate(r.joinedAt as string) : undefined,
  }
}

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
    currentUserId = Number(result.id)
    return { id: Number(result.id), name: result.name }
  }

  async login(identifier: string, password: string): Promise<User> {
    const c = getCore()
    if (!c) return new AccountBridgeMock().login(identifier, password)
    const result = parse<{ id: string; name: string }>(
      c.ngomaLogin(identifier, hashPassword(password))
    )
    currentUserId = Number(result.id)
    return { id: Number(result.id), name: result.name }
  }

  async register(name: string, identifier: string, password: string): Promise<User> {
    const c = getCore()
    if (!c) return new AccountBridgeMock().register(name, identifier, password)
    const result = parse<{ id: string; name: string }>(
      c.ngomaRegister(name, identifier, hashPassword(password))
    )
    currentUserId = Number(result.id)
    return { id: Number(result.id), name: result.name }
  }

  async logout(): Promise<void> {
    const c = getCore()
    if (c) c.ngomaLogout()
    currentUserId = null
  }

  // Gestion des utilisateurs (M1 SI, module 1) : ?crans livr?s par Foundation avec le mock du mod?le.
  // ? brancher sur votre core : ngomaSearchUsers, ngomaGetUser, ngomaUpdateProfile, ngomaSetActive.
  // Lecture branchée sur le core (nécessaire aux noms d'auteurs des publications) ;
  // updateProfile et deleteAccount restent sur le mock pour l'instant.
  private readonly usersMock = new AccountBridgeMock()
  private readonly poller = new Poller()

  subscribe(listener: () => void): () => void {
    if (!getCore()) return this.usersMock.subscribe(listener)
    return this.poller.subscribe(listener)
  }

  users(query = ''): User[] {
    const c = getCore()
    if (!c) return this.usersMock.users(query)
    return (parseSoft<any[]>(c.ngomaUsers(query)) ?? []).map(toUser)
  }

  user(id: number): User | undefined {
    const c = getCore()
    if (!c) return this.usersMock.user(id)
    const r = parseSoft<any>(c.ngomaGetUser(String(swapMe(id))))
    return r ? toUser(r) : undefined
  }
  /** Modifie le nom et/ou la description de l'utilisateur connecté (RG-U8, RG-U9). */
  updateProfile(patch: { name?: string; bio?: string }): void {
    const c = getCore()
    if (!c) return this.usersMock.updateProfile(patch)
    if (currentUserId == null) return
    const me = parseSoft<any>(c.ngomaGetUser(String(currentUserId)))
    if (!me) return
    // Un champ absent du patch garde sa valeur ; un nom vide garde l'ancien nom (géré par le core)
    const name = patch.name ?? ''
    const bio  = patch.bio ?? (me.bio as string) ?? ''
    report(c.ngomaUpdateProfile(String(currentUserId), name, bio), 'modification du profil')
    this.poller.emit()
  }

  /** Supprime définitivement le compte de l'utilisateur connecté (RG-U12), puis ferme la session. */
  async deleteAccount(): Promise<void> {
    const c = getCore()
    if (!c) return this.usersMock.deleteAccount()
    if (currentUserId == null) return
    report(c.ngomaDeleteAccount(String(currentUserId)), 'suppression du compte')
    currentUserId = null
    this.poller.emit()
  }
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
    // Polling 5 s — simule les notifications en mode offline
    this._pollTimer = setInterval(() => {
      this.reload()
      this.emit()
    }, 5000)
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
        attachment:     r.attachment ? (r.attachment as string) : undefined,
        edited:         Boolean(r.edited),
        deleted:        Boolean(r.deleted),
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
      opts?.attachment ?? '',
    )
    this._loadMessages(String(conversationId))
    this.emit()
  }

  edit(messageId: number, text: string): void {
    const c = getCore()
    if (!c) return
    const raw = c.ngomaEditMessage(String(messageId), this.currentUserId, text)
    if (raw.includes('"error"')) console.error('[NGoma] modification du message refusée :', raw)
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

  /** « Supprimer pour moi » : hors conception v2 (ajout non prévu par les interfaces Foundation). */
  removeForMe(_messageId: number): void {
    console.warn('[NGoma] « Supprimer pour moi » n\'est pas prévu par la conception v2.')
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
  /** Supprime la conversation du côté de l'utilisateur connecté seulement. */
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
// VideoBridgeCore : publications, likes, commentaires et réponses (module 3)
// =============================================================================

/** RG-F3 : taille maximale d'une photo ou d'une vidéo enregistrée sans serveur (500 Ko). */
const MAX_MEDIA_BYTES = 500 * 1024

/** Contenu des médias déjà lus (data URL) : un média ne change jamais, on ne le lit qu'une fois (RG-F5). */
const mediaCache = new Map<number, string>()

function mediaUrl(mediaId: number): string {
  let url = mediaCache.get(mediaId)
  if (url === undefined) {
    const c = getCore()
    const r = c ? parseSoft<{ data: string }>(c.ngomaMediaData(String(mediaId))) : null
    url = r?.data ?? ''
    mediaCache.set(mediaId, url)
  }
  return url
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload  = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

function toPost(r: any): Post {
  const type = r.mediaType === 'image' || r.mediaType === 'video' ? (r.mediaType as 'image' | 'video') : undefined
  return {
    id:        Number(r.id),
    authorId:  swapMe(Number(r.authorId)),
    caption:   r.caption as string,
    mediaUrl:  r.mediaId ? mediaUrl(Number(r.mediaId)) : '',
    mediaType: type,
    time:      parseDate(r.publishedAt as string),
    edited:    Boolean(r.edited),
    likes:     ((r.likes as number[]) ?? []).map(swapMe),
  }
}

function toComment(r: any, postId: number): PostComment {
  return {
    id:       Number(r.id),
    postId,
    authorId: swapMe(Number(r.authorId)),
    text:     r.text as string,
    time:     parseDate(r.createdAt as string),
    parentId: r.parentId ? Number(r.parentId) : undefined,
    edited:   Boolean(r.edited),
    deleted:  Boolean(r.deleted),
    likes:    ((r.likes as number[]) ?? []).map(swapMe),
  }
}

class VideoBridgeCore implements VideoBridge {

  private readonly mock   = new VideoBridgeMock()
  private readonly poller = new Poller(() => this.invalidate())
  // Cache relu à chaque changement et toutes les 5 s : les écrans relisent le bridge à chaque affichage.
  private postsCache: Post[] | null = null
  private commentsCache = new Map<number, PostComment[]>()

  subscribe(listener: () => void): () => void {
    if (!getCore()) return this.mock.subscribe(listener)
    return this.poller.subscribe(listener)
  }

  private invalidate(): void {
    this.postsCache = null
    this.commentsCache.clear()
  }

  private changed(): void {
    this.invalidate()
    this.poller.emit()
  }

  /** Identifiant réel de l'utilisateur connecté, ou null (l'action est alors ignorée). */
  private me(): string | null {
    if (currentUserId == null) { console.warn('[NGoma] aucun utilisateur connecté'); return null }
    return String(currentUserId)
  }

  // ── Lecture ────────────────────────────────────────────────────────────────

  posts(): Post[] {
    const c = getCore()
    if (!c) return this.mock.posts()
    if (!this.postsCache) this.postsCache = (parseSoft<any[]>(c.ngomaPosts()) ?? []).map(toPost)
    return this.postsCache
  }

  userPosts(userId: number): Post[] {
    const c = getCore()
    if (!c) return this.mock.userPosts(userId)
    return (parseSoft<any[]>(c.ngomaUserPosts(String(swapMe(userId)))) ?? []).map(toPost)
  }

  comments(postId: number): PostComment[] {
    const c = getCore()
    if (!c) return this.mock.comments(postId)
    let list = this.commentsCache.get(postId)
    if (!list) {
      list = (parseSoft<any[]>(c.ngomaComments(String(postId))) ?? []).map((r) => toComment(r, postId))
      this.commentsCache.set(postId, list)
    }
    return list
  }

  // ── Publications ───────────────────────────────────────────────────────────

  /**
   * Publie une légende et/ou une photo ou vidéo de 500 Ko maximum (RG-P1, RG-F2, RG-F3, RG-F6).
   * La lecture du fichier est asynchrone : la publication apparaît dans le fil dès qu'elle est enregistrée.
   * L'écran n'utilise pas la valeur renvoyée ; on renvoie une publication provisoire.
   */
  publish(input: { caption: string; file?: File }): Post {
    const c = getCore()
    if (!c) return this.mock.publish(input)
    const pending: Post = { id: -1, authorId: ME, caption: input.caption, mediaUrl: '', time: Date.now(), likes: [] }
    const me = this.me()
    if (!me) return pending

    const file = input.file
    if (!file) {
      report(c.ngomaCreatePost(me, input.caption, '', '', '0'), 'publication')
      this.changed()
      return pending
    }
    const type = file.type.startsWith('image/') ? 'image' : file.type.startsWith('video/') ? 'video' : ''
    if (!type) { console.error('[NGoma] publication refusée : seules les photos et les vidéos sont acceptées.'); return pending }
    if (file.size > MAX_MEDIA_BYTES) {
      console.error(`[NGoma] publication refusée : le fichier fait ${Math.round(file.size / 1024)} Ko, le maximum est 500 Ko.`)
      return pending
    }
    readAsDataUrl(file)
      .then((data) => {
        report(c.ngomaCreatePost(me, input.caption, type, data, String(file.size)), 'publication')
        this.changed()
      })
      .catch((e) => console.error('[NGoma] lecture du fichier impossible :', e))
    return pending
  }

  editPost(postId: number, caption: string): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.editPost(postId, caption)
    if (!me) return
    report(c.ngomaEditPost(String(postId), me, caption), 'modification de la publication')
    this.changed()
  }

  removePost(postId: number): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.removePost(postId)
    if (!me) return
    report(c.ngomaDeletePost(String(postId), me), 'suppression de la publication')
    this.changed()
  }

  toggleLike(postId: number): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.toggleLike(postId)
    if (!me) return
    report(c.ngomaToggleLike(String(postId), me), 'like')
    this.changed()
  }

  // ── Commentaires et réponses ───────────────────────────────────────────────

  addComment(postId: number, text: string, parentId?: number): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.addComment(postId, text, parentId)
    if (!me) return
    report(c.ngomaAddComment(String(postId), me, text, String(parentId ?? 0)), 'commentaire')
    this.changed()
  }

  editComment(commentId: number, text: string): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.editComment(commentId, text)
    if (!me) return
    report(c.ngomaEditComment(String(commentId), me, text), 'modification du commentaire')
    this.changed()
  }

  removeComment(commentId: number): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.removeComment(commentId)
    if (!me) return
    report(c.ngomaRemoveComment(String(commentId), me), 'suppression du commentaire')
    this.changed()
  }

  toggleCommentLike(commentId: number): void {
    const c = getCore(); const me = this.me()
    if (!c) return this.mock.toggleCommentLike(commentId)
    if (!me) return
    report(c.ngomaToggleCommentLike(String(commentId), me), 'like du commentaire')
    this.changed()
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
// Branché : VideoBridgeCore (TModeler si le core est chargé, mock sinon).
export const videoBridge: VideoBridge = new VideoBridgeCore()

export * from './types'
export { ME } from './mock'
export { makeMessagingBridge as createMessagingBridge }
