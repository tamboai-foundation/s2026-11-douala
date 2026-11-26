import { useEffect, useState } from 'react'
import type { Conversation, Message, MessagingBridge, User } from '../lib/bridge'
import ConversationView from './ConversationView'
import NewConversationDialog from './NewConversationDialog'

const FILTERS = ['Toutes', 'Non lues', 'Épinglées', 'Archivées'] as const
type Filter = (typeof FILTERS)[number]

export function useBridge<T>(bridge: { subscribe(listener: () => void): () => void }, read: () => T): T {
  // Les mocks émettent à chaque changement : on force un nouveau rendu et on relit l'état du pont.
  const [version, setVersion] = useState(0)
  useEffect(() => bridge.subscribe(() => setVersion((v) => v + 1)), [bridge])
  void version
  return read()
}

export const hour = (t: number) =>
  new Date(t).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })

export function Avatar({ name, size = 44 }: { name: string; size?: number }) {
  return (
    <div
      className="rounded-full bg-brand-100 text-brand-dark font-bold flex items-center justify-center shrink-0"
      style={{ width: size, height: size, fontSize: size / 2.4 }}
    >
      {name.slice(0, 1).toUpperCase()}
    </div>
  )
}

export default function Home({
  user,
  bridge,
  onLogout,
  openChatWith,
  onChatOpened,
}: {
  user: User
  bridge: MessagingBridge
  onLogout: () => void
  openChatWith?: string | null
  onChatOpened?: () => void
}) {
  const convs = useBridge(bridge, () => bridge.conversations())
  const [selected, setSelected] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<Filter>('Toutes')
  const [showNewDialog, setShowNewDialog] = useState(false)
  const [dialogError, setDialogError]     = useState('')

  const shown = convs
    .filter((c) => {
      if (filter === 'Archivées') return c.archived
      if (c.archived) return false
      if (filter === 'Non lues') return c.unread > 0
      if (filter === 'Épinglées') return c.pinned
      return true
    })
    .filter((c) => c.title.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => Number(b.pinned) - Number(a.pinned))

  const current = convs.find((c) => c.id === selected)

  // Wrapper : après désarchivage, revenir sur "Toutes"
  const wrappedBridge: MessagingBridge = {
    subscribe:          (l) => bridge.subscribe(l),
    conversations:      ()  => bridge.conversations(),
    messages:           (id) => bridge.messages(id),
    lastMessage:        (id) => bridge.lastMessage(id),
    send:               (id, t, o) => bridge.send(id, t, o),
    edit:               (id, t) => bridge.edit(id, t),
    remove:             (id) => bridge.remove(id),
    removeForMe:        (id) => bridge.removeForMe(id),
    react:              (id, e) => bridge.react(id, e),
    markRead:           (id) => bridge.markRead(id),
    setPinned:          (id, v) => bridge.setPinned(id, v),
    setMuted:           (id, v) => bridge.setMuted(id, v),
    createConversation: (t) => bridge.createConversation(t),
    deleteConversation: (id) => { bridge.deleteConversation(id); setSelected(null) },
    setArchived(id: number, value: boolean) {
      bridge.setArchived(id, value)
      if (!value) setFilter('Toutes')
    },
  }

  // « Écrire un message » depuis l'écran Personnes : ouvre la conversation de cette personne (la crée si besoin).
  useEffect(() => {
    if (!openChatWith) return
    const existing = bridge.conversations().find((c) => c.title === openChatWith)
    setSelected(existing ? existing.id : bridge.createConversation(openChatWith))
    onChatOpened?.()
  }, [openChatWith, bridge, onChatOpened])

  function newConversation() {
    setDialogError('')
    setShowNewDialog(true)
  }

  function handleDialogSelect(phone: string) {
    const id = wrappedBridge.createConversation(phone)
    if (id > 0) {
      setShowNewDialog(false)
      setDialogError('')
      setSelected(id)
    } else {
      // Laisser le dialog ouvert et afficher l'erreur dedans
      setDialogError(`Aucun utilisateur trouvé avec le numéro « ${phone} ».`)
    }
  }

  return (
    <>
    <div className="h-full flex bg-page">
      <aside className="w-[360px] shrink-0 bg-white border-r border-hairline flex flex-col">
        <div className="flex items-center gap-3 p-4">
          <Avatar name={user.name} size={36} />
          <h2 className="font-extrabold text-xl flex-1">Messages</h2>
          <button className="text-sm font-semibold text-brand" onClick={newConversation}>
            + Nouveau
          </button>
          <button className="text-sm text-secondary" onClick={onLogout}>
            Quitter
          </button>
        </div>
        <div className="px-3">
          <input
            className="w-full rounded-xl bg-muted px-4 py-2.5 text-sm outline-none"
            placeholder="Rechercher"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="flex gap-2 p-3">
          {FILTERS.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`rounded-full px-3 py-1.5 text-[13px] font-medium ${
                f === filter ? 'bg-brand text-white' : 'bg-muted text-secondary'
              }`}
            >
              {f}
            </button>
          ))}
        </div>
        <div className="flex-1 overflow-y-auto border-t border-hairline">
          {shown.map((c) => (
            <Row
              key={c.id}
              c={c}
              last={bridge.lastMessage(c.id)}
              active={c.id === selected}
              onClick={() => {
                setSelected(c.id)
                bridge.markRead(c.id)
              }}
            />
          ))}
        </div>
      </aside>
      <main className="flex-1 min-w-0">
        {current ? (
          <ConversationView key={current.id} conv={current} bridge={wrappedBridge} currentUserId={user.id} />
        ) : (
          <div className="h-full flex flex-col items-center justify-center text-secondary">
            <p className="text-3xl font-extrabold text-brand">NGoma</p>
            <p>Sélectionnez une conversation</p>
          </div>
        )}
      </main>
    </div>

    {/* Dialog de création de conversation */}
    {showNewDialog && (
      <NewConversationDialog
        currentUserId={String(user.id)}
        onSelect={handleDialogSelect}
        onClose={() => { setShowNewDialog(false); setDialogError('') }}
        externalError={dialogError}
      />
    )}
  </>
  )
}

function Row({
  c,
  last,
  active,
  onClick,
}: {
  c: Conversation
  last?: Message
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-3 text-left border-b border-hairline ${
        active ? 'bg-brand-50' : 'hover:bg-page'
      }`}
    >
      <Avatar name={c.title} />
      <div className="flex-1 min-w-0">
        <div className="flex items-center">
          <span className="font-semibold truncate flex-1">
            {c.pinned ? '📌 ' : ''}
            {c.title}
          </span>
          {last && <span className="text-xs text-secondary/70">{hour(last.time)}</span>}
        </div>
        <p className="text-[13px] text-secondary truncate">
          {last ? last.text || 'Pièce jointe' : 'Aucun message'}
        </p>
      </div>
      {c.muted && <span>🔕</span>}
      {c.unread > 0 && (
        <span className="bg-brand text-white text-xs font-bold rounded-full px-2 py-0.5">
          {c.unread}
        </span>
      )}
    </button>
  )
}
