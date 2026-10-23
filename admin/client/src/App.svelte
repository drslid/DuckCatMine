<script lang="ts">
  import { modal } from './modal';
  import { onMount, tick } from 'svelte';
  import { api, setCsrf, demo, fold, asset } from './api';
  import Icon from './Icon.svelte';
  import Chat from './Chat.svelte';
  import { chatViewport } from './chat-viewport';
  import Health from './Health.svelte';
  import { serverHealth } from './health.js';
  let healthClock = $state(Date.now());
  let user = $state(''),
    loading = $state(true),
    username = $state(''),
    password = $state(''),
    confirmation = $state(''),
    authError = $state(''),
    authBusy = $state(false),
    activation = $state(''),
    activated = $state(false);
  let section = $state('home'),
    status = $state<any>({}),
    messages = $state<any[]>([]),
    connected = $state(false),
    notice = $state(''),
    noticeError = $state(false),
    chatOpen = $state(false),
    unread = $state(0),
    readId = '',
    chatInitialized = false,
    selectedPlayer = $state(''),
    newPlayer = $state(''),
    palette = $state(false),
    query = $state(''),
    notifications = $state(false),
    installPrompt: any = null;
  let health = $derived(serverHealth(status, healthClock));
  let World = $state<any>(null),
    Player = $state<any>(null),
    Settings = $state<any>(null),
    Apocalypse = $state<any>(null),
    apocalypseInstalled = $state(false),
    Notifications = $state<any>(null),
    settingsInitial = $state('game');
  let source: EventSource,
    fallback,
    noticeTimer,
    demoTimer,
    paletteInput = $state<HTMLInputElement>();
  const baseNav = [
    ['home', 'home', 'Accueil'],
    ['players', 'users', 'Joueurs'],
    ['world', 'map', 'Monde'],
    ['chat', 'chat', 'Chat'],
    ['settings', 'settings', 'Réglages'],
  ];
  let nav = $derived(
    apocalypseInstalled
      ? [
          ...baseNav.slice(0, 3),
          ['apocalypse', 'skull', 'Ground Zero'],
          ...baseNav.slice(3),
        ]
      : baseNav,
  );
  const actions = [
    ['Joueurs et permissions', 'players'],
    ['Inventaire, objets et skins', 'players'],
    ['Carte et positions des joueurs', 'world'],
    ['Importer une construction', 'world'],
    ['Faire apparaître une créature', 'world'],
    ['Ground Zero : mode survie zombie', 'apocalypse'],
    ['Chat de la partie', 'chat'],
    ['Sauvegardes', 'settings', 'backups'],
    ['Extensions, catalogue et import .jar', 'settings', 'plugins'],
    ['Consommation et performances', 'settings', 'performance'],
    ['Console Minecraft', 'settings', 'console'],
  ];
  let online = $derived(
    (status.players?.match(/players online:\s*(.*)$/i)?.[1] || '')
      .split(',')
      .map((p) => p.trim())
      .filter(Boolean),
  );
  let people = $derived([
    ...new Set([
      ...online,
      ...(status.whitelist || []).map((p) => p.name),
      ...(status.ops || []).map((p) => p.name),
    ]),
  ] as string[]);
  let results = $derived(
    actions.filter((a) => fold(a[0]).includes(fold(query))),
  );
  let tps = $derived(status.tps?.match(/\d+\.\d+/)?.[0] || '—');
  function toast(message, error = false) {
    notice = message;
    noticeError = error;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => (notice = ''), 6500);
  }
  function markRead() {
    if (document.hidden) return;
    readId = messages.at(-1)?.id || readId;
    unread = 0;
    try {
      localStorage.setItem(`duckcatmine.chat.read.${user}`, readId);
    } catch {}
    document.title = 'DuckCatMine';
    (navigator as any).clearAppBadge?.().catch(() => {});
  }
  function receiveChat(data) {
    messages = data.messages || [];
    if (!chatInitialized) {
      chatInitialized = true;
      if (!readId) readId = messages.at(-1)?.id || '';
    }
    if (section === 'chat' || chatOpen) {
      markRead();
      return;
    }
    const last = messages.findIndex((m) => m.id === readId);
    unread = last >= 0 ? messages.length - last - 1 : messages.length;
    document.title = unread ? `(${unread}) DuckCatMine` : 'DuckCatMine';
  }
  async function refresh() {
    try {
      status = await api('/api/status');
    } catch (e) {
      if (e.message === 'Connexion requise.') signOut();
      else toast(e.message, true);
    }
  }
  function clearConnection() {
    source?.close();
    clearInterval(fallback);
    clearInterval(demoTimer);
    connected = false;
  }
  function signOut() {
    clearConnection();
    user = '';
    setCsrf();
    chatOpen = false;
    messages = [];
    unread = 0;
    password = '';
    document.title = 'DuckCatMine';
  }
  async function begin(account) {
    user = account.username;
    setCsrf(account.csrf);
    try {
      readId = localStorage.getItem(`duckcatmine.chat.read.${user}`) || '';
    } catch {}
    chatInitialized = false;
    try {
      apocalypseInstalled = !!(await api('/api/apocalypse')).installed;
    } catch {}
    const requestedSection = location.hash.slice(1);
    await navigate(
      ['world', 'players', 'settings', 'chat', 'apocalypse'].includes(
        requestedSection,
      )
        ? requestedSection
        : 'home',
    );
    if (demo) {
      status = await api('/api/status');
      receiveChat(await api('/api/chat'));
      connected = true;
      demoTimer = setInterval(async () => {
        receiveChat(await api('/api/chat'));
        status = await api('/api/status');
      }, 1000);
      return;
    }
    source = new EventSource('/api/events');
    source.addEventListener('status', (e) => {
      status = JSON.parse((e as MessageEvent).data);
    });
    source.addEventListener('chat', (e) =>
      receiveChat(JSON.parse((e as MessageEvent).data)),
    );
    source.onopen = () => {
      connected = true;
      clearInterval(fallback);
      fallback = null;
    };
    source.onerror = () => {
      connected = false;
      if (!fallback)
        fallback = setInterval(async () => {
          try {
            await api('/api/me');
            await refresh();
            receiveChat(await api('/api/chat'));
          } catch (e) {
            if (e.message === 'Connexion requise.') signOut();
          }
        }, 10000);
    };
  }
  async function navigate(value, sub = 'game') {
    section = value;
    palette = false;
    query = '';
    chatOpen = false;
    if (value === 'world' && !World)
      World = (await import('./World.svelte')).default;
    if (value === 'players' && !Player)
      Player = (await import('./Player.svelte')).default;
    if (value === 'apocalypse' && !Apocalypse)
      Apocalypse = (await import('./Apocalypse.svelte')).default;
    if (value === 'settings') {
      settingsInitial = sub;
      if (!Settings) Settings = (await import('./Settings.svelte')).default;
    }
    if (value === 'chat') markRead();
    history.replaceState(
      null,
      '',
      `${location.pathname}${value === 'home' ? '' : '#' + value}`,
    );
    window.scrollTo({ top: 0 });
  }
  async function login(e) {
    e.preventDefault();
    authBusy = true;
    authError = '';
    try {
      if (activation) {
        if (password !== confirmation)
          throw new Error('Les deux mots de passe doivent être identiques.');
        await api('/api/activate', 'POST', { token: activation, password });
        activation = '';
        activated = true;
        password = '';
        confirmation = '';
        history.replaceState(null, '', '/');
      } else {
        await begin(await api('/api/login', 'POST', { username, password }));
        password = '';
      }
    } catch (e) {
      authError = e.message;
    } finally {
      authBusy = false;
    }
  }
  async function logout() {
    try {
      await api('/api/logout', 'POST');
    } finally {
      signOut();
    }
  }
  async function allowPlayer(e) {
    e.preventDefault();
    try {
      await api('/api/command', 'POST', {
        command: `whitelist add ${newPlayer}`,
      });
      toast(`${newPlayer} peut rejoindre le serveur.`);
      newPlayer = '';
      await refresh();
    } catch (e) {
      toast(e.message, true);
    }
  }
  async function openNotifications() {
    Notifications = (await import('./Notifications.svelte')).default;
    notifications = true;
  }
  async function install() {
    if (installPrompt) {
      installPrompt.prompt();
      await installPrompt.userChoice;
      installPrompt = null;
    } else
      toast(
        /iPad|iPhone|iPod/.test(navigator.userAgent)
          ? 'Dans Safari : Partager → Sur l’écran d’accueil.'
          : 'Dans le menu du navigateur, choisis « Installer l’application ».',
      );
  }
  onMount(() => {
    const healthTimer = setInterval(() => (healthClock = Date.now()), 15000);
    let cancelled = false;
    const setup = async () => {
      try {
        const token = new URLSearchParams(location.hash.slice(1)).get('token');
        if (location.pathname === '/activate' && token) {
          activation = token;
          const info = await api('/api/invite-info', 'POST', { token });
          username = info.username;
          history.replaceState(null, '', '/activate');
        } else {
          const account = await api('/api/me');
          if (!cancelled) await begin(account);
        }
      } catch (e) {
        if (activation) authError = e.message;
      } finally {
        loading = false;
      }
    };
    setup();
    if (!demo && 'serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    }
    const pushMessage = (e) => {
      if (e.data?.type === 'open-chat' && user) navigate('chat');
    };
    navigator.serviceWorker?.addEventListener('message', pushMessage);
    const key = (e) => {
      if (e.key === 'Escape') {
        palette = false;
        notifications = false;
        chatOpen = false;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        palette = true;
        tick().then(() => paletteInput?.focus());
      }
    };
    const visibility = () => {
      if (!document.hidden && (section === 'chat' || chatOpen)) markRead();
    };
    const prompt = (e) => {
      e.preventDefault();
      installPrompt = e;
    };
    window.addEventListener('beforeinstallprompt', prompt);
    document.addEventListener('keydown', key);
    document.addEventListener('visibilitychange', visibility);
    return () => {
      cancelled = true;
      clearInterval(healthTimer);
      clearConnection();
      clearTimeout(noticeTimer);
      document.removeEventListener('keydown', key);
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('beforeinstallprompt', prompt);
      navigator.serviceWorker?.removeEventListener('message', pushMessage);
    };
  });
</script>

{#if loading}<div class="loading-screen">
    <div class="brand">
      <img src={asset('logo.svg')} alt="" /><span>DuckCatMine</span>
    </div>
    <p class="muted">Un instant, on prépare ton espace…</p>
  </div>
{:else if !user}<main class="auth-screen">
    <div class="auth-art">
      <div class="brand"><img src={asset('logo.svg')} alt="" />DuckCatMine</div>
      <div>
        <p class="eyebrow">UN MONDE À PARTAGER</p>
        <h1>Moins de réglages.<br />Plus d’aventures.</h1>
        <p>
          Ton serveur Minecraft, simplement.<br />Depuis ton ordinateur ou ton
          téléphone.
        </p>
      </div>
      <small>Auto-hébergé. Open source. À toi de jouer.</small>
    </div>
    <section class="auth-form">
      <div class="mobile-brand brand">
        <img src={asset('logo.svg')} alt="" />DuckCatMine
      </div>
      <p class="eyebrow">{activation ? 'BIENVENUE' : 'TON ESPACE PRIVÉ'}</p>
      <h2>
        {activation ? `Bienvenue, ${username}.` : 'Content de te revoir.'}
      </h2>
      <p class="muted">
        {activation
          ? 'Choisis ton mot de passe pour activer ton compte.'
          : 'Connecte-toi pour retrouver ton monde.'}
      </p>
      {#if activated}<p class="success">
          Compte activé. Tu peux te connecter.
        </p>{/if}
      <form onsubmit={login}>
        {#if !activation}<label
            >Pseudo Minecraft<input
              autocomplete="username"
              bind:value={username}
              required
              placeholder="Ton pseudo"
            /></label
          >{/if}<label
          >Mot de passe<input
            type="password"
            autocomplete={activation ? 'new-password' : 'current-password'}
            bind:value={password}
            required
            minlength={activation ? 12 : undefined}
            maxlength="128"
          /></label
        >{#if activation}<label
            >Confirmer le mot de passe<input
              type="password"
              autocomplete="new-password"
              bind:value={confirmation}
              required
              minlength="12"
            /></label
          >{/if}{#if authError}<p class="error" role="alert">
            {authError}
          </p>{/if}<button class="primary full" disabled={authBusy}
          >{authBusy
            ? 'Un instant…'
            : activation
              ? 'Activer mon compte'
              : 'Se connecter'}<Icon name="arrow" /></button
        >
      </form>
      <button class="quiet full" onclick={install}
        ><Icon name="home" />Installer sur mon téléphone</button
      ><small class="muted">Accès réservé aux membres autorisés.</small>
    </section>
  </main>
{:else}<div
    class="app-shell"
    class:chat-active={section === 'chat'}
    use:chatViewport
  >
    <aside class="sidebar">
      <a
        class="brand"
        href="./"
        onclick={(e) => {
          e.preventDefault();
          navigate('home');
        }}
        ><img src={asset('logo.svg')} alt="" /><span
          >DuckCatMine<small>Ton monde, simplement.</small></span
        ></a
      ><button
        class="search-trigger"
        onclick={() => {
          palette = true;
          tick().then(() => paletteInput?.focus());
        }}><Icon name="search" /><span>Une action…</span><kbd>⌘ K</kbd></button
      >
      <nav aria-label="Navigation principale">
        {#each nav as [id, icon, label]}<button
            class:active={section === id}
            onclick={() => navigate(id)}
            ><Icon name={icon} /><span>{label}</span
            >{#if id === 'chat' && unread}<b class="badge"
                >{unread > 99 ? '99+' : unread}</b
              >{/if}</button
          >{/each}
      </nav>
      <div class="sidebar-bottom">
        <span class="connection" class:live={connected}></span><small
          >{connected ? 'Connecté en direct' : 'Reconnexion…'}</small
        >
        <div class="sidebar-account">
          <span class="account-avatar">{user.slice(0, 2).toUpperCase()}</span
          ><strong>{user}</strong><button
            class="icon-button"
            aria-label="Se déconnecter"
            onclick={logout}><Icon name="logout" size={17} /></button
          >
        </div>
      </div>
    </aside>
    <div class="main-shell">
      <header class="app-header">
        <a
          class="brand mobile-brand"
          href="./"
          onclick={(e) => {
            e.preventDefault();
            navigate('home');
          }}><img src={asset('logo.svg')} alt="" /><span>DuckCatMine</span></a
        ><span class="desktop-title"
          >{nav.find((n) => n[0] === section)?.[2]}</span
        >
        <div class="spacer"></div>
        {#if demo}<span class="demo-badge">Démo</span>{/if}
        <Health
          {health}
          open={() => navigate('settings', 'performance')}
        /><button
          class="icon-button"
          aria-label="Rechercher une action"
          onclick={() => {
            palette = true;
            tick().then(() => paletteInput?.focus());
          }}><Icon name="search" /></button
        ><button
          class="icon-button"
          aria-label="Notifications et installation"
          onclick={openNotifications}><Icon name="bell" /></button
        >
      </header>
      <main
        class="page-content"
        class:chat-view={section === 'chat'}
        id="main-content"
      >
        {#if section === 'home'}<section class="welcome">
            <div>
              <p class="eyebrow">TON PROCHAIN MOMENT ENSEMBLE</p>
              <h1>On se retrouve<br />dans le monde ?</h1>
              <p>
                Un village à agrandir, une expédition à préparer. À vous de
                jouer.
              </p>
              <button
                class="join-address"
                onclick={async () => {
                  try {
                    await navigator.clipboard.writeText(
                      status.address || location.hostname,
                    );
                    toast('Adresse du serveur copiée.');
                  } catch {
                    toast(status.address || location.hostname);
                  }
                }}
                ><span>{status.address || location.hostname}</span><Icon
                  name="copy"
                  size={17}
                /></button
              >
              {#if status.gameVersion}<p class="join-version">
                  Rejoins avec <strong
                    >Minecraft Java {status.gameVersion}</strong
                  > sur PC.
                </p>{/if}
            </div>
            <div class="welcome-landscape" aria-hidden="true">
              <svg viewBox="0 0 420 260"
                ><path d="m40 150 170-85 170 85-170 85z" fill="#49765a" /><path
                  d="m40 150 170 85v25L40 175z"
                  fill="#294b3b"
                /><path d="m210 235 170-85v25l-170 85z" fill="#355c48" /><path
                  d="m160 139 56-28 56 28-56 28z"
                  fill="#ad9a6f"
                /><path d="m160 139 56 28v48l-56-28z" fill="#68765c" /><path
                  d="m216 167 56-28v48l-56 28z"
                  fill="#4c6150"
                /><path d="m148 138 68-64 68 64-68 34z" fill="#bdad80" /><path
                  d="m216 74 68 64-68 34z"
                  fill="#928461"
                /><path
                  d="m190 170 16 8v27l-16-8z"
                  fill="#253e31"
                />{#each [[100, 140], [310, 155], [145, 100]] as [x, y]}<path
                    d={`m${x} ${y} 11 5v35l-11-5z`}
                    fill="#7c684d"
                  /><path
                    d={`m${x - 23} ${y + 5} 29-66 29 66-29 16z`}
                    fill="#6c9b68"
                  /><path
                    d={`m${x + 6} ${y - 61} 29 66-29 16z`}
                    fill="#47764e"
                  />{/each}</svg
              >
            </div>
          </section>
          <div class="home-actions">
            <button class="action-card" onclick={() => navigate('players')}
              ><span class="action-icon"><Icon name="users" size={24} /></span
              ><strong
                >{online.length
                  ? `${online.length} joueur${online.length > 1 ? 's' : ''} en ligne`
                  : 'Les joueurs'}</strong
              ><small>Inventaires, objets et apparences</small><Icon
                name="arrow"
              /></button
            ><button class="action-card" onclick={() => navigate('world')}
              ><span class="action-icon mint"
                ><Icon name="map" size={24} /></span
              ><strong>Explorer et construire</strong><small
                >Une carte, toutes les possibilités</small
              ><Icon name="arrow" /></button
            ><button
              class="action-card"
              onclick={() => navigate('settings', 'backups')}
              ><span class="action-icon sand"
                ><Icon name="save" size={24} /></span
              ><strong>Protéger le monde</strong><small
                >Sauvegardes et restauration</small
              ><Icon name="arrow" /></button
            >
          </div>
          <div class="home-bottom">
            <article class="card">
              <div class="card-heading">
                <h2>Qui est là ?</h2>
                <button class="quiet" onclick={() => navigate('players')}
                  >Voir les joueurs <Icon name="arrow" size={16} /></button
                >
              </div>
              {#if online.length}<div class="people-preview">
                  {#each online as p}<button
                      class="person-chip"
                      onclick={async () => {
                        selectedPlayer = p;
                        await navigate('players');
                      }}
                      ><span class="account-avatar">{p.slice(0, 2)}</span><span
                        >{p}</span
                      ><i class="connection live"></i></button
                    >{/each}
                </div>{:else}<p class="muted">
                  Le monde est calme. La prochaine aventure commence avec vous.
                </p>{/if}
            </article>
            <article class="card server-health" data-health={health.level}>
              <div>
                <span class="health-orb"
                  ><Icon
                    name={health.level === 'healthy' ? 'check' : 'heart'}
                  /></span
                >
                <div>
                  <h2>
                    {health.label}
                  </h2>
                  <p class="muted">
                    {health.detail}
                  </p>
                </div>
              </div>
              <button
                class="quiet"
                onclick={() => navigate('settings', 'performance')}
                >Voir la santé du serveur <Icon
                  name="arrow"
                  size={16}
                /></button
              >
            </article>
          </div>
        {:else if section === 'players'}<div class="section-heading">
            <div>
              <p class="eyebrow">UNE FICHE, TOUT CE QU’IL FAUT</p>
              <h1>Les joueurs</h1>
            </div>
            <span class="pill">{online.length} en ligne</span>
          </div>
          <div class="players-workspace">
            <aside class="card player-list">
              <h2>Les membres</h2>
              {#each people as p}<button
                  class:active={selectedPlayer === p}
                  onclick={() => (selectedPlayer = p)}
                  ><span class="account-avatar">{p.slice(0, 2)}</span><span
                    ><strong>{p}</strong><small
                      >{online.includes(p) ? 'En ligne' : 'Hors ligne'}</small
                    ></span
                  ><Icon name="chevron" size={16} /></button
                >{/each}
              <details class="disclosure">
                <summary>Inviter un joueur à la partie</summary>
                <form onsubmit={allowPlayer}>
                  <label
                    >Pseudo Minecraft<input
                      bind:value={newPlayer}
                      pattern={'[A-Za-z0-9_]{3,16}'}
                      maxlength="16"
                      required
                      placeholder="Pseudo exact"
                    /></label
                  ><button class="primary full">Autoriser</button>
                </form>
              </details>
            </aside>
            <article class="card player-card">
              {#if selectedPlayer && Player}{#key selectedPlayer}<Player
                    name={selectedPlayer}
                    online={online.includes(selectedPlayer)}
                    op={(status.ops || []).some(
                      (p) => p.name === selectedPlayer,
                    )}
                    {toast}
                  />{/key}{:else}<div class="empty large">
                  <span class="action-icon"
                    ><Icon name="users" size={30} /></span
                  >
                  <h2>Choisis un joueur.</h2>
                  <p>
                    Son inventaire, ses objets, ses skins et ses permissions
                    seront réunis ici.
                  </p>
                  {#if people.length}<button
                      class="secondary"
                      onclick={() => (selectedPlayer = people[0])}
                      >Voir {people[0]}</button
                    >{/if}
                </div>{/if}
            </article>
          </div>
        {:else if section === 'world' && World}<World
            players={online}
            start={status.mapStart}
            policy={status.mapPolicy}
            openPlayer={(name) => {
              selectedPlayer = name;
              navigate('players');
            }}
            {toast}
          />
        {:else if section === 'apocalypse' && Apocalypse}<Apocalypse
            {toast}
            openPlayer={(name) => {
              selectedPlayer = name;
              navigate('players');
            }}
          />
        {:else if section === 'chat'}<div class="section-heading">
            <div>
              <p class="eyebrow">ON GARDE LE LIEN</p>
              <h1>Le chat de la partie</h1>
            </div>
            <button class="secondary" onclick={openNotifications}
              ><Icon name="bell" />Notifications</button
            >
          </div>
          <article class="card chat-page">
            <Chat {messages} {user} {toast} />
          </article>
          <p class="muted chat-help">
            Les messages envoyés ici apparaissent dans Minecraft avec ton pseudo
            et la mention Web.
          </p>
        {:else if section === 'settings' && Settings}{#key settingsInitial}<Settings
              {status}
              {toast}
              initial={settingsInitial}
            />{/key}
        {:else}<p class="muted">Ouverture de cet espace…</p>{/if}
      </main>
    </div>
    <nav class="mobile-nav" aria-label="Navigation mobile">
      {#each nav as [id, icon, label]}<button
          class:active={section === id}
          onclick={() => navigate(id)}
          ><Icon name={icon} /><span>{label}</span
          >{#if id === 'chat' && unread}<b class="badge"
              >{unread > 99 ? '99+' : unread}</b
            >{/if}</button
        >{/each}
    </nav>
    {#if section !== 'chat'}{#if chatOpen}<aside
          class="chat-drawer"
          aria-label="Chat permanent"
        >
          <div class="chat-drawer-heading">
            <span class="connection live"></span><strong
              >Chat de la partie</strong
            ><button class="quiet push-right" onclick={() => navigate('chat')}
              >Agrandir</button
            ><button
              class="icon-button"
              aria-label="Fermer le chat"
              onclick={() => (chatOpen = false)}><Icon name="close" /></button
            >
          </div>
          <Chat {messages} {user} {toast} compact />
        </aside>{:else}<button
          class="chat-launcher"
          aria-label={unread
            ? `${unread} nouveaux messages. Ouvrir le chat`
            : 'Ouvrir le chat'}
          onclick={() => {
            chatOpen = true;
            markRead();
          }}
          ><Icon name="chat" /><span
            >{unread
              ? `${unread} nouveau${unread > 1 ? 'x' : ''} message${unread > 1 ? 's' : ''}`
              : 'Chat de la partie'}</span
          >{#if unread}<b class="badge">{unread > 99 ? '99+' : unread}</b
            >{:else}<i class="connection live"></i>{/if}</button
        >{/if}{/if}
  </div>{/if}
{#if notice}<div
    class="toast"
    class:error={noticeError}
    role={noticeError ? 'alert' : 'status'}
  >
    <Icon name={noticeError ? 'bell' : 'check'} /><span>{notice}</span><button
      class="icon-button"
      aria-label="Fermer le message"
      onclick={() => (notice = '')}><Icon name="close" size={16} /></button
    >
  </div>{/if}
{#if palette}<div class="modal-backdrop" role="presentation">
    <div
      class="modal command-palette"
      use:modal={{ close: () => (palette = false) }}
      role="dialog"
      aria-modal="true"
      aria-label="Rechercher une action"
      tabindex="-1"
    >
      <div class="catalog-search">
        <Icon name="search" /><input
          bind:this={paletteInput}
          bind:value={query}
          placeholder="Qu’est-ce que tu veux faire ?"
          aria-label="Rechercher une action"
        /><button
          class="icon-button"
          aria-label="Fermer la recherche"
          onclick={() => (palette = false)}><Icon name="close" /></button
        >
      </div>
      {#each results as action}<button
          onclick={() => navigate(action[1], action[2])}
          ><span>{action[0]}</span><Icon name="arrow" size={16} /></button
        >{/each}{#if !results.length}<p class="empty">
          Aucune action trouvée.
        </p>{/if}
    </div>
  </div>{/if}
{#if notifications && Notifications}<div
    class="modal-backdrop"
    role="presentation"
  >
    <div
      class="modal"
      use:modal={{ close: () => (notifications = false) }}
      role="dialog"
      aria-modal="true"
      aria-label="Notifications et installation"
      tabindex="-1"
    >
      <div class="modal-heading">
        <h2>Garder le lien</h2>
        <button
          class="icon-button"
          aria-label="Fermer"
          onclick={() => (notifications = false)}><Icon name="close" /></button
        >
      </div>
      <Notifications {toast} />
      <details class="disclosure">
        <summary>Installer sur mon téléphone</summary>
        <p>
          Ajoute DuckCatMine à ton écran d’accueil pour le retrouver comme une
          application.
        </p>
        <button class="secondary" onclick={install}>Comment installer</button>
      </details>
      <button class="quiet full" onclick={logout}
        ><Icon name="logout" />Se déconnecter</button
      >
    </div>
  </div>{/if}
