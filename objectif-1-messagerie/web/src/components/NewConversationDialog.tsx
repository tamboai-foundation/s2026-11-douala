import { useEffect, useRef, useState } from 'react'
import { getCore } from '../lib/tm'
import { Avatar } from './Home'

type Contact = { id: string; name: string; phone: string }

interface Props {
  currentUserId: string
  onSelect: (phone: string) => void
  onClose: () => void
  externalError?: string   // erreur remontée par Home si la création échoue
}

export default function NewConversationDialog({ currentUserId, onSelect, onClose, externalError }: Props) {
  const [query, setQuery]       = useState('')
  const [contacts, setContacts] = useState<Contact[]>([])
  const [error, setError]       = useState('')
  const inputRef                = useRef<HTMLInputElement>(null)

  // Afficher l'erreur externe (ex. numéro introuvable) sans fermer le dialog
  const displayError = externalError || error

  useEffect(() => { inputRef.current?.focus() }, [])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])

  // Recherche en temps réel
  useEffect(() => {
    setError('')
    const c = getCore()
    if (!c || query.trim().length === 0) { setContacts([]); return }
    try {
      const raw  = c.ngomaSearchUsers(query.trim(), currentUserId)
      const list = JSON.parse(raw)
      setContacts(Array.isArray(list) ? list : [])
    } catch { setContacts([]) }
  }, [query, currentUserId])

  // Appelé quand on clique sur un contact ou on valide la saisie directe
  function handleSelect(phone: string) {
    setError('')
    if (!phone.trim()) return
    // On passe directement le numéro à Home.tsx qui gère la création
    // et retourne -1 si l'utilisateur n'existe pas
    onSelect(phone.trim())
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/30 backdrop-blur-sm"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="w-full max-w-sm mx-4 bg-white rounded-3xl shadow-2xl flex flex-col overflow-hidden">

        {/* En-tête */}
        <div className="flex items-center gap-3 px-5 pt-5 pb-3">
          <h2 className="font-extrabold text-[18px] flex-1 text-ink">Nouvelle conversation</h2>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-muted text-secondary flex items-center justify-center hover:bg-hairline transition-colors"
          >✕</button>
        </div>

        {/* Barre de recherche */}
        <div className="px-5 pb-3">
          <div className="flex items-center gap-2 rounded-xl bg-muted px-4 py-2.5">
            <input
              ref={inputRef}
              className="flex-1 bg-transparent text-sm outline-none placeholder:text-secondary"
              placeholder="Nom ou numéro de téléphone"
              value={query}
              onChange={(e) => { setQuery(e.target.value); setError('') }}
              onKeyDown={(e) => { if (e.key === 'Enter' && query.trim()) handleSelect(query.trim()) }}
            />
            {query && (
              <button onClick={() => { setQuery(''); setError('') }} className="text-secondary hover:text-ink">✕</button>
            )}
          </div>
        </div>

        {/* Erreur */}
        {displayError && (
          <p className="px-5 pb-2 text-sm text-negative font-medium">{displayError}</p>
        )}

        {/* Résultats */}
        <div className="overflow-y-auto max-h-72 border-t border-hairline">

          {query.trim().length === 0 && (
            <p className="text-sm text-secondary text-center py-8 px-5">
              Tapez un nom ou un numéro pour rechercher un contact.
            </p>
          )}

          {query.trim().length > 0 && contacts.length === 0 && (
            <div className="flex flex-col items-center py-8 gap-3 px-5">
              <p className="text-sm text-secondary">Aucun résultat pour « {query.trim()} »</p>
              <button
                onClick={() => handleSelect(query.trim())}
                className="rounded-xl bg-brand text-white text-sm font-semibold px-5 py-2.5 hover:bg-brand-dark transition-colors"
              >
                Démarrer avec ce numéro
              </button>
            </div>
          )}

          {contacts.map((contact) => (
            <button
              key={contact.id}
              onClick={() => handleSelect(contact.phone)}
              className="w-full flex items-center gap-3 px-5 py-3 text-left hover:bg-page transition-colors border-b border-hairline last:border-b-0"
            >
              <Avatar name={contact.name} size={40} />
              <div className="flex-1 min-w-0">
                <p className="font-semibold text-[15px] truncate">{contact.name}</p>
                <p className="text-sm text-secondary truncate">{contact.phone}</p>
              </div>
              <span className="text-brand text-xl">›</span>
            </button>
          ))}
        </div>

        {/* Pied */}
        <div className="px-5 py-4 border-t border-hairline">
          <button
            onClick={onClose}
            className="w-full rounded-xl py-3 text-sm font-semibold text-secondary bg-muted hover:bg-hairline transition-colors"
          >
            Annuler
          </button>
        </div>
      </div>
    </div>
  )
}
