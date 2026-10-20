<script lang="ts">
  import { onMount } from 'svelte';
  import { api, demo } from './api';
  let { toast } = $props();
  let statusText = $state('Vérification de ce téléphone…'),
    active = $state(false),
    busy = $state(false),
    subscription: any,
    registration: ServiceWorkerRegistration;
  const supported =
    !demo &&
    'serviceWorker' in navigator &&
    'Notification' in window &&
    'PushManager' in window;
  const standalone =
    matchMedia('(display-mode: standalone)').matches ||
    (navigator as any).standalone;
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent);
  async function inspect() {
    if (!supported) {
      statusText = demo
        ? 'Les notifications sont désactivées dans la démo.'
        : 'Ce navigateur ne propose pas les notifications web.';
      return;
    }
    registration = await navigator.serviceWorker.register('/sw.js');
    subscription = await registration.pushManager.getSubscription();
    active = !!subscription && Notification.permission === 'granted';
    statusText = active
      ? 'Notifications activées sur cet appareil.'
      : ios && !standalone
        ? 'Ajoute l’application à l’écran d’accueil, puis ouvre son icône.'
        : 'Active les notifications pour recevoir les messages du chat.';
  }
  async function enable() {
    busy = true;
    try {
      if (ios && !standalone)
        throw new Error(
          'Sur iPhone : Safari → Partager → Sur l’écran d’accueil. Ouvre ensuite l’icône.',
        );
      const permission = await Notification.requestPermission();
      if (permission !== 'granted')
        throw new Error(
          'Autorise les notifications dans les réglages du navigateur.',
        );
      const config = await api('/api/push/config');
      const raw = atob(config.publicKey.replace(/-/g, '+').replace(/_/g, '/'));
      const key = Uint8Array.from(raw, (c) => c.charCodeAt(0));
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: key,
      });
      await api('/api/push/subscribe', 'POST', subscription.toJSON());
      await inspect();
      toast('Notifications activées.');
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function disable() {
    busy = true;
    try {
      await api('/api/push/unsubscribe', 'POST', {
        endpoint: subscription.endpoint,
      });
      await subscription.unsubscribe();
      await inspect();
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function test(local = false) {
    try {
      if (local)
        await registration.showNotification('DuckCatMine', {
          body: 'L’affichage des notifications fonctionne.',
          icon: '/app-icon-192.png',
        });
      else
        await api('/api/push/test', 'POST', {
          endpoint: subscription.endpoint,
        });
      toast(
        local
          ? 'Notification locale demandée.'
          : 'Notification envoyée au service de ce téléphone.',
      );
    } catch (e) {
      toast(e.message, true);
    }
  }
  onMount(() => {
    inspect().catch((e) => (statusText = e.message));
  });
</script>

<p>{statusText}</p>
<div class="inline wrap">
  {#if active}<button class="secondary" onclick={() => test()}
      >Tester l’envoi</button
    ><button class="quiet" onclick={() => test(true)}>Tester l’affichage</button
    ><button class="quiet" disabled={busy} onclick={disable}>Désactiver</button
    >{:else}<button
      class="primary"
      disabled={!supported || busy || (ios && !standalone)}
      onclick={enable}>Activer les notifications</button
    >{/if}
</div>
{#if ios}<small class="muted"
    >Sur iPhone, utilise l’icône de l’écran d’accueil et vérifie que le mode
    Concentration autorise les notifications.</small
  >{/if}
