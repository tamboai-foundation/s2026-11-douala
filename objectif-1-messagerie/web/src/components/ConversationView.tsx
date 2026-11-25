import { useEffect, useRef, useState } from 'react'
import { ME, type Conversation, type Message, type MessagingBridge } from '../lib/bridge'
import { Avatar, hour, useBridge } from './Home'
import {
  PencilIcon, TrashIcon, ReplyIcon, EyeOffIcon, UsersIcon,
  PinIcon, BellOffIcon, ArchiveIcon, XCircleIcon, CheckIcon,
} from './Icons'

const EMOJIS = ['👍', '❤️', '😂', '😮']

// Dialog de confirmation générique
function ConfirmDialog({
  message,
  onConfirm,
  onCancel,
}: {
  message: string
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/30 backdrop-blur-sm">
      <div className="w-full max-w-xs mx-4 bg-white rounded-2xl shadow-2xl p-6 flex flex-col gap-4">
        <p className="text-sm text-ink text-center">{message}</p>
        <div className="flex gap-3">
          <button
            onClick={onCancel}
            className="flex-1 rounded-xl py-2.5 text-sm font-semibold bg-muted text-secondary hover:bg-hairline transition-colors"
          >
            Annuler
          </button>
          <button
            onClick={onConfirm}
            className="flex-1 rounded-xl py-2.5 text-sm font-semibold bg-negative text-white hover:opacity-90 transition-opacity"
          >
            Supprimer
          </button>
        </div>
      </div>
    </div>
  )
}

// Dialog choix suppression message
function DeleteMessageDialog({
  onForMe,
  onForAll,
  onCancel,
  isMine,
}: {
  onForMe: () => void
  onForAll: () => void
  onCancel: () => void
  isMine: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/30 backdrop-blur-sm">
      <div className="w-full max-w-xs mx-4 bg-white rounded-2xl shadow-2xl p-6 flex flex-col gap-3">
        <p className="text-sm font-semibold text-ink text-center">Supprimer ce message ?</p>
        <button
          onClick={onForMe}
          className="flex items-center gap-3 rounded-xl px-4 py-3 bg-muted hover:bg-hairline transition-colors text-sm text-left"
        >
          <EyeOffIcon size={16} className="text-secondary shrink-0" />
          <span>Supprimer pour moi</span>
        </button>
        {isMine && (
          <button
            onClick={onForAll}
            className="flex items-center gap-3 rounded-xl px-4 py-3 bg-muted hover:bg-hairline transition-colors text-sm text-left"
          >
            <UsersIcon size={16} className="text-secondary shrink-0" />
            <span>Supprimer pour tout le monde</span>
          </button>
        )}
        <button
          onClick={onCancel}
          className="rounded-xl py-2.5 text-sm font-semibold text-secondary bg-muted hover:bg-hairline transition-colors"
        >
          Annuler
        </button>
      </div>
    </div>
  )
}

export default function ConversationView({
  conv,
  bridge,
  currentUserId,
}: {
  conv: Conversation
  bridge: MessagingBridge
  currentUserId: number
}) {
  const messages = useBridge(bridge, () =>
    bridge.messages(conv.id).filter((m) => !m.deletedForMe)
  )
  const [input, setInput]       = useState('')
  const [replyTo, setReplyTo]   = useState<Message | null>(null)
  const [editing, setEditing]   = useState<Message | null>(null)
  const [menuFor, setMenuFor]   = useState<number | null>(null)
  const [headerMenu, setHeaderMenu] = useState(false)
  const [deleteMsg, setDeleteMsg]   = useState<Message | null>(null)
  const [confirmDelConv, setConfirmDelConv] = useState(false)
  const endRef  = useRef<HTMLDivElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
    bridge.markRead(conv.id)
  }, [messages.length, conv.id, bridge])

  function submit() {
    const text = input.trim()
    if (!text) return
    if (editing) bridge.edit(editing.id, text)
    else bridge.send(conv.id, text, { replyToId: replyTo?.id })
    setInput('')
    setReplyTo(null)
    setEditing(null)
  }

  const banner = editing
    ? 'Modification du message'
    : replyTo
    ? `Réponse à : ${replyTo.text}`
    : null

  return (
    <>
    <div className="h-full flex flex-col">
      {/* Header */}
      <header className="flex items-center gap-3 bg-white border-b border-hairline px-4 py-2.5 relative">
        <Avatar name={conv.title} size={40} />
        <h2 className="font-bold text-[17px] flex-1">{conv.title}</h2>
        <button
          className="p-2 rounded-full hover:bg-muted transition-colors"
          onClick={() => setHeaderMenu(!headerMenu)}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" className="text-secondary">
            <circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>
          </svg>
        </button>

        {headerMenu && (
          <div className="absolute right-3 top-14 z-10 bg-white rounded-2xl shadow-lg border border-hairline py-1.5 w-56">
            <MenuItem
              icon={<PinIcon size={15} />}
              label={conv.pinned ? 'Désépingler' : 'Épingler'}
              onClick={() => { bridge.setPinned(conv.id, !conv.pinned); setHeaderMenu(false) }}
            />
            <MenuItem
              icon={<BellOffIcon size={15} />}
              label={conv.muted ? 'Réactiver les notifications' : 'Mettre en sourdine'}
              onClick={() => { bridge.setMuted(conv.id, !conv.muted); setHeaderMenu(false) }}
            />
            <MenuItem
              icon={<ArchiveIcon size={15} />}
              label={conv.archived ? 'Désarchiver' : 'Archiver'}
              onClick={() => { bridge.setArchived(conv.id, !conv.archived); setHeaderMenu(false) }}
            />
            <div className="border-t border-hairline my-1" />
            <MenuItem
              icon={<XCircleIcon size={15} />}
              label="Supprimer la conversation"
              danger
              onClick={() => { setHeaderMenu(false); setConfirmDelConv(true) }}
            />
          </div>
        )}
      </header>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-3 flex flex-col gap-1.5">
        {messages.map((m) => {
          const mine   = m.authorId === currentUserId
          const quoted = m.replyToId
            ? messages.find((x) => x.id === m.replyToId)
            : undefined

          return (
            <div key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div className={`relative flex flex-col ${mine ? 'items-end' : 'items-start'}`}>
                <button
                  disabled={m.deleted}
                  onClick={() => setMenuFor(menuFor === m.id ? null : m.id)}
                  className={`max-w-[460px] text-left rounded-2xl px-3 py-2 ${
                    mine ? 'bg-brand text-white' : 'bg-white text-ink'
                  }`}
                >
                  {quoted && (
                    <p className="text-xs opacity-75 truncate mb-1 border-l-2 border-current pl-2">
                      {quoted.deleted ? 'Message supprimé' : quoted.text}
                    </p>
                  )}
                  {m.deleted ? (
                    <span className="italic opacity-60 text-sm">Message supprimé</span>
                  ) : (
                    <>
                      {m.attachment && (
                        <p className="text-sm font-semibold mb-1">📎 {m.attachment}</p>
                      )}
                      {m.text && (
                        <p className="whitespace-pre-wrap break-words">{m.text}</p>
                      )}
                    </>
                  )}
                  <p className="text-[11px] opacity-65 text-right mt-0.5">
                    {hour(m.time)}{m.edited ? ' · modifié' : ''}
                  </p>
                </button>

                {/* Réactions */}
                {Object.keys(m.reactions).length > 0 && (
                  <span className="mt-0.5 bg-muted rounded-full px-2 py-0.5 text-xs">
                    {Object.entries(m.reactions).map(([e, n]) => `${e} ${n}`).join('  ')}
                  </span>
                )}

                {/* Menu contextuel */}
                {menuFor === m.id && !m.deleted && (
                  <div
                    className={`absolute z-10 top-full mt-1 ${
                      mine ? 'right-0' : 'left-0'
                    } bg-white rounded-2xl shadow-lg border border-hairline py-2 w-52`}
                  >
                    {/* Emojis */}
                    <div className="flex justify-around px-3 pb-2 border-b border-hairline mb-1">
                      {EMOJIS.map((e) => (
                        <button
                          key={e}
                          className="text-lg hover:scale-125 transition-transform"
                          onClick={() => { bridge.react(m.id, e); setMenuFor(null) }}
                        >
                          {e}
                        </button>
                      ))}
                    </div>

                    {/* Actions avec icônes */}
                    <ActionItem
                      icon={<ReplyIcon size={15} />}
                      label="Répondre"
                      onClick={() => { setReplyTo(m); setEditing(null); setMenuFor(null) }}
                    />
                    {mine && !m.deleted && (
                      <ActionItem
                        icon={<PencilIcon size={15} />}
                        label="Modifier"
                        onClick={() => {
                          setEditing(m)
                          setReplyTo(null)
                          setInput(m.text)
                          setMenuFor(null)
                        }}
                      />
                    )}
                    <ActionItem
                      icon={<TrashIcon size={15} />}
                      label="Supprimer"
                      danger
                      onClick={() => { setDeleteMsg(m); setMenuFor(null) }}
                    />
                  </div>
                )}
              </div>
            </div>
          )
        })}
        <div ref={endRef} />
      </div>

      {/* Bannière réponse/modification */}
      {banner && (
        <div className="flex items-center bg-brand-50 text-brand-dark text-[13px] px-4 py-1.5 border-t border-hairline">
          <span className="flex-1 truncate">{banner}</span>
          <button
            onClick={() => { setEditing(null); setReplyTo(null); setInput('') }}
            className="ml-2 text-brand hover:text-brand-dark"
          >
            <XCircleIcon size={16} />
          </button>
        </div>
      )}

      {/* Zone de saisie */}
      <footer className="flex items-center gap-2 bg-white border-t border-hairline p-3">
        <input
          ref={fileRef}
          type="file"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) bridge.send(conv.id, '', { attachment: f.name })
            e.target.value = ''
          }}
        />
        <button
          className="p-2 rounded-full text-secondary hover:bg-muted transition-colors"
          title="Joindre un fichier"
          onClick={() => fileRef.current?.click()}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
            <path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <input
          className="flex-1 rounded-full bg-muted px-4 py-2.5 text-sm outline-none"
          placeholder="Message"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
        <button
          onClick={submit}
          className="w-10 h-10 rounded-full bg-brand hover:bg-brand-dark text-white flex items-center justify-center transition-colors"
        >
          {editing ? <CheckIcon size={18} /> : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
              <path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>
            </svg>
          )}
        </button>
      </footer>
    </div>

    {/* Dialog suppression message */}
    {deleteMsg && (
      <DeleteMessageDialog
        isMine={deleteMsg.authorId === currentUserId}
        onForMe={() => {
          bridge.removeForMe(deleteMsg.id)
          setDeleteMsg(null)
        }}
        onForAll={() => {
          bridge.remove(deleteMsg.id)
          setDeleteMsg(null)
        }}
        onCancel={() => setDeleteMsg(null)}
      />
    )}

    {/* Dialog suppression conversation */}
    {confirmDelConv && (
      <ConfirmDialog
        message="Supprimer cette conversation ?"
        onConfirm={() => {
          bridge.deleteConversation(conv.id)
          setConfirmDelConv(false)
        }}
        onCancel={() => setConfirmDelConv(false)}
      />
    )}
    </>
  )
}

function MenuItem({
  icon,
  label,
  danger = false,
  onClick,
}: {
  icon: React.ReactNode
  label: string
  danger?: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left hover:bg-page transition-colors ${
        danger ? 'text-negative' : 'text-ink'
      }`}
    >
      <span className={danger ? 'text-negative' : 'text-secondary'}>{icon}</span>
      {label}
    </button>
  )
}

function ActionItem({
  icon,
  label,
  danger = false,
  onClick,
}: {
  icon: React.ReactNode
  label: string
  danger?: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-2 text-sm text-left hover:bg-page transition-colors ${
        danger ? 'text-negative' : 'text-ink'
      }`}
    >
      <span className={danger ? 'text-negative' : 'text-secondary'}>{icon}</span>
      {label}
    </button>
  )
}
