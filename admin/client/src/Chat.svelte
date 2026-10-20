<script lang="ts">
  import { tick } from 'svelte';
  import { api } from './api';
  import Icon from './Icon.svelte';
  let {
    messages = [],
    user = '',
    toast,
    compact = false,
  }: {
    messages?: any[];
    user?: string;
    toast: any;
    compact?: boolean;
  } = $props();
  let text = $state(''),
    busy = $state(false),
    feed: HTMLDivElement,
    following = $state(true);
  $effect(() => {
    messages.length;
    if (following)
      tick().then(() => {
        if (feed) feed.scrollTop = feed.scrollHeight;
      });
  });
  async function send(e) {
    e.preventDefault();
    if (!text.trim() || busy) return;
    busy = true;
    try {
      await api('/api/chat/send', 'POST', { message: text.trim() });
      text = '';
      following = true;
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
</script>

<div class="chat-thread" class:compact>
  <div
    class="chat-feed"
    bind:this={feed}
    role="log"
    aria-label="Messages de la partie"
    aria-live="polite"
    onscroll={() =>
      (following = feed.scrollHeight - feed.scrollTop - feed.clientHeight < 60)}
  >
    {#each messages as message (message.id)}<div
        class="message"
        class:own={message.author === user}
      >
        <div class="message-meta">
          <strong>{message.author}</strong>{#if message.source === 'web'}<span
              class="source-tag">Web</span
            >{/if}<time>{message.at}</time>
        </div>
        <p>{message.message}</p>
      </div>{/each}
    {#if !messages.length}<div class="empty">
        <Icon name="chat" size={30} />
        <p>La conversation commence ici.</p>
      </div>{/if}
  </div>
  {#if !following}<button
      class="new-messages"
      onclick={() => {
        following = true;
        feed.scrollTop = feed.scrollHeight;
      }}>Voir les derniers messages ↓</button
    >{/if}
  <form class="chat-compose" onsubmit={send}>
    <input
      aria-label="Message pour les joueurs"
      bind:value={text}
      required
      maxlength="180"
      placeholder={`Écrire en tant que ${user}…`}
      autocomplete="off"
    /><button
      class="primary icon-button"
      aria-label="Envoyer le message"
      disabled={busy || !text.trim()}><Icon name="arrow" /></button
    >
  </form>
</div>
