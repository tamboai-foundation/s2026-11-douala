import { useState, type FormEvent } from 'react'
import { accountBridge, type User } from '../lib/bridge'

const ERROR_LABELS: Record<string, string> = {
  INVALID_CREDENTIALS: 'Numéro ou mot de passe incorrect.',
  ACCOUNT_DISABLED:    'Ce compte est désactivé.',
  PHONE_ALREADY_USED:  'Ce numéro est déjà utilisé.',
  NAME_REQUIRED:       'Le nom est obligatoire.',
  NAME_TOO_SHORT:      'Le nom doit comporter au moins 2 caractères.',
  NAME_TOO_LONG:       'Le nom ne doit pas dépasser 50 caractères.',
  PHONE_REQUIRED:      'Le numéro de téléphone est obligatoire.',
  PASSWORD_REQUIRED:   'Le mot de passe est obligatoire.',
  NOT_READY:           'L\'application n\'est pas encore prête. Réessayez.',
}

function friendlyError(raw: string): string {
  return ERROR_LABELS[raw] ?? 'Une erreur est survenue. Réessayez.'
}

export default function Auth({ onDone }: { onDone: (u: User) => void }) {
  const [register, setRegister] = useState(false)
  const [name, setName]         = useState('')
  const [id, setId]             = useState('')
  const [pwd, setPwd]           = useState('')
  const [busy, setBusy]         = useState(false)
  const [error, setError]       = useState('')

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const u = register
        ? await accountBridge.register(name, id, pwd)
        : await accountBridge.login(id, pwd)
      onDone(u)
    } catch (err: unknown) {
      const code = err instanceof Error ? err.message : String(err)
      setError(friendlyError(code))
    } finally {
      setBusy(false)
    }
  }

  const input =
    'w-full rounded-xl border border-hairline px-4 py-3 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/20'

  return (
    <div className="h-full flex items-center justify-center bg-page p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-sm bg-white rounded-3xl shadow-lg p-8 flex flex-col gap-4"
      >
        <h1 className="text-3xl font-extrabold text-brand">NGoma</h1>
        <p className="text-secondary text-sm -mt-2">
          {register ? 'Créer un compte' : 'Connexion'}
        </p>

        {register && (
          <input
            className={input}
            placeholder="Nom"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        )}
        <input
          className={input}
          placeholder="Numéro de téléphone"
          value={id}
          onChange={(e) => setId(e.target.value)}
        />
        <input
          className={input}
          type="password"
          placeholder="Mot de passe"
          value={pwd}
          onChange={(e) => setPwd(e.target.value)}
        />

        {/* Message d'erreur */}
        {error && (
          <p className="text-sm text-negative font-medium -mt-1">{error}</p>
        )}

        <button
          disabled={busy}
          className="rounded-xl bg-brand hover:bg-brand-dark text-white font-semibold py-3 disabled:opacity-60 transition-colors"
        >
          {busy ? 'Chargement…' : register ? "S'inscrire" : 'Se connecter'}
        </button>
        <button
          type="button"
          className="text-sm text-brand font-medium"
          onClick={() => { setRegister(!register); setError('') }}
        >
          {register ? "J'ai déjà un compte" : 'Créer un compte'}
        </button>
      </form>
    </div>
  )
}
