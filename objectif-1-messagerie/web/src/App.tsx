import { useEffect, useState } from 'react'
import { accountBridge, createMessagingBridge, type User } from './lib/bridge'
import type { MessagingBridge } from './lib/types'
import { startTModeler } from './lib/tm'
import Splash from './components/Splash'
import Auth from './components/Auth'
import Home from './components/Home'

type Phase = 'splash' | 'auth' | 'home'

export default function App() {
  const [phase, setPhase]       = useState<Phase>('splash')
  const [user, setUser]         = useState<User | null>(null)
  const [bridge, setBridge]     = useState<MessagingBridge | null>(null)

  useEffect(() => {
    let alive = true
    ;(async () => {
      await startTModeler()
      const u = await accountBridge.restoreSession()
      if (!alive) return
      if (u) {
        setUser(u)
        setBridge(createMessagingBridge(u.id))
        setPhase('home')
      } else {
        setPhase('auth')
      }
    })()
    return () => { alive = false }
  }, [])

  function handleLogin(u: User) {
    setUser(u)
    setBridge(createMessagingBridge(u.id))
    setPhase('home')
  }

  async function handleLogout() {
    await accountBridge.logout()
    setUser(null)
    setBridge(null)
    setPhase('auth')
  }

  if (phase === 'splash') return <Splash />

  if (phase === 'auth' || !user || !bridge) {
    return <Auth onDone={handleLogin} />
  }

  return (
    <Home
      user={user}
      bridge={bridge}
      onLogout={handleLogout}
    />
  )
}
