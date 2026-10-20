<script lang="ts">
  import { modal } from './modal';
  import { onMount } from 'svelte';
  import { api, command, iconUrl } from './api';
  import Icon from './Icon.svelte';
  import SkinFigure from './SkinFigure.svelte';
  let {
    name,
    online,
    op = false,
    toast,
  }: { name: string; online: boolean; op?: boolean; toast: any } = $props();
  let tab = $state('profile'),
    details = $state<any>(null),
    error = $state(''),
    loading = $state(false),
    busy = $state(false),
    inv = $state('main'),
    slot = $state<any>(null),
    amount = $state(1),
    moving = $state<any>(null),
    choosingItem = $state(false),
    xp = $state(1),
    target = $state(''),
    gamemode = $state('survival'),
    roleConfirm = $state(false),
    reason = $state('');
  let Items = $state<any>(null),
    Skins = $state<any>(null),
    sequence = 0;
  let entries = $derived(details?.inventory?.[inv] || []);
  async function load() {
    if (!online) return;
    const id = ++sequence;
    loading = true;
    error = '';
    try {
      const result = await api(
        `/api/players/${name}/details?section=${tab === 'inventory' ? inv : 'none'}`,
      );
      if (id === sequence) {
        details = result;
        gamemode = result.status.gamemode;
        slot = null;
      }
    } catch (e) {
      if (id === sequence) error = e.message;
    } finally {
      if (id === sequence) loading = false;
    }
  }
  async function chooseTab(value) {
    tab = value;
    slot = null;
    moving = null;
    if (value === 'objects') Items = (await import('./Items.svelte')).default;
    else if (value === 'skins')
      Skins = (await import('./Skins.svelte')).default;
    else await load();
  }
  async function act(operation, extra = {}) {
    busy = true;
    try {
      await api('/api/players/live-action', 'POST', {
        player: name,
        operation,
        ...extra,
      });
      toast(`Modification appliquée à ${name}.`);
      await load();
    } catch (e) {
      toast(e.message, true);
      await load();
    } finally {
      busy = false;
    }
  }
  async function run(value) {
    busy = true;
    try {
      const r = await command(value);
      toast(r.result || 'Modification appliquée.');
      await load();
    } catch (e) {
      toast(e.message, true);
    } finally {
      busy = false;
    }
  }
  async function chooseSlot(item) {
    if (moving) {
      const from = moving;
      moving = null;
      if (from.slot !== item.slot)
        await act('swap', {
          slot: from.slot,
          rev: from.rev,
          targetSlot: item.slot,
          targetRev: item.rev,
        });
    } else {
      slot = item;
      amount = item.amount || 1;
    }
  }
  onMount(load);
</script>

<div class="player-header">
  <div class="player-portrait">
    <SkinFigure src={`/api/skins/player-image/${name}`} />
  </div>
  <div>
    <p class="eyebrow">{online ? 'EN LIGNE' : 'HORS LIGNE'}</p>
    <h2>{name}</h2>
    <span class="pill"
      >{details?.status.op || op ? 'Administrateur Minecraft' : 'Joueur'}</span
    >
  </div>
  <button
    class="icon-button push-right"
    title="Actualiser le joueur"
    onclick={load}
    disabled={loading}><Icon name="refresh" /></button
  >
</div>
<div class="segments player-tabs">
  {#each [['profile', 'Profil'], ['inventory', 'Inventaire'], ['objects', 'Objets'], ['skins', 'Skins']] as [id, label]}<button
      class:active={tab === id}
      disabled={!online && id !== 'profile'}
      onclick={() => chooseTab(id)}>{label}</button
    >{/each}
</div>
{#if error}<div class="error" role="alert">
    {error}<button class="quiet" onclick={load}>Réessayer</button>
  </div>{/if}
{#if loading}<p class="muted" role="status">Lecture du joueur…</p>{/if}
{#if tab === 'profile'}
  {#if details?.status}<div class="player-vitals">
      <div>
        <Icon name="heart" /><strong
          >{Math.ceil(details.status.health / 2)}<small>
            / {Math.ceil(details.status.maxHealth / 2)}</small
          ></strong
        ><span>Cœurs</span>
      </div>
      <div>
        <Icon name="box" /><strong
          >{details.status.food}<small> / 20</small></strong
        ><span>Faim</span>
      </div>
      <div>
        <Icon name="shield" /><strong>{details.status.level}</strong><span
          >Niveau</span
        >
      </div>
    </div>
    <div class="inline wrap">
      <button class="secondary" disabled={busy} onclick={() => act('heal')}
        ><Icon name="heart" />Soigner</button
      ><button class="secondary" disabled={busy} onclick={() => act('feed')}
        >Nourrir</button
      ><button class="secondary" onclick={() => chooseTab('objects')}
        ><Icon name="box" />Donner un objet</button
      >
    </div>
    <div class="form-grid">
      <label
        >Mode de jeu<select bind:value={gamemode}
          ><option value="survival">Survie</option><option value="creative"
            >Créatif</option
          ><option value="adventure">Aventure</option><option value="spectator"
            >Spectateur</option
          ></select
        ></label
      ><button
        class="secondary align-end"
        disabled={busy}
        onclick={() => run(`gamemode ${gamemode} ${name}`)}>Appliquer</button
      >
    </div>
    <details class="disclosure">
      <summary>Expérience et téléportation</summary>
      <div class="inline">
        <label
          >Niveaux d’XP<input
            type="number"
            min="-1000"
            max="1000"
            bind:value={xp}
          /></label
        ><button
          class="secondary align-end"
          disabled={busy}
          onclick={() => act('xp', { levels: xp })}>Ajouter / retirer</button
        >
      </div>
      <form
        class="inline"
        onsubmit={(e) => {
          e.preventDefault();
          run(`tp ${name} ${target}`);
        }}
      >
        <input
          aria-label="Joueur de destination"
          bind:value={target}
          placeholder="Vers quel joueur ?"
          required
          pattern={'[A-Za-z0-9_]{3,16}'}
        /><button class="secondary">Téléporter</button>
      </form>
      <small class="muted"
        >Position : {details.status.world} · {details.status.x}, {details.status
          .y}, {details.status.z}</small
      >
    </details>{:else if !online}<p class="empty">
      Ce joueur est déconnecté. Il peut rejoindre la partie pour modifier son
      inventaire ou son apparence.
    </p>{/if}
  <details class="disclosure">
    <summary>Permissions et accès</summary>
    <p>Les droits administrateur donnent accès aux commandes Minecraft.</p>
    {#if roleConfirm}<div class="confirmation">
        <p>{op ? 'Retirer' : 'Donner'} les droits administrateur de {name} ?</p>
        <div class="inline">
          <button
            class="primary"
            disabled={busy}
            onclick={async () => {
              await run(`${op ? 'deop' : 'op'} ${name}`);
              roleConfirm = false;
            }}>Confirmer</button
          ><button class="quiet" onclick={() => (roleConfirm = false)}
            >Annuler</button
          >
        </div>
      </div>{:else}<button
        class="secondary"
        onclick={() => (roleConfirm = true)}
        ><Icon name="shield" />{op
          ? 'Retirer les droits admin'
          : 'Passer administrateur'}</button
      >{/if}
    <div class="inline wrap">
      <button class="quiet" onclick={() => run(`whitelist add ${name}`)}
        >Autoriser sur le serveur</button
      ><button
        class="quiet"
        onclick={() => {
          if (confirm(`Retirer ${name} de la liste blanche ?`))
            run(`whitelist remove ${name}`);
        }}>Retirer de la liste blanche</button
      >
    </div>
    <details class="disclosure">
      <summary>Modération</summary><input
        aria-label="Motif de modération"
        maxlength="80"
        bind:value={reason}
        placeholder="Motif (facultatif)"
      />
      <div class="inline wrap">
        <button class="secondary" onclick={() => run(`kick ${name}`)}
          >Expulser</button
        ><button
          class="danger"
          onclick={() => {
            if (confirm(`Bannir ${name} ?`))
              run(`ban ${name}${reason ? ' ' + reason : ''}`);
          }}>Bannir</button
        ><button class="quiet" onclick={() => run(`pardon ${name}`)}
          >Débannir</button
        >
      </div>
    </details>
  </details>
{:else if tab === 'inventory'}
  <div class="segments">
    {#each [['main', 'Sac'], ['equipment', 'Équipement'], ['ender', 'Ender chest']] as [id, label]}<button
        class:active={inv === id}
        onclick={() => {
          inv = id;
          moving = null;
          load();
        }}>{label}</button
      >{/each}
  </div>
  <p class="muted">
    {moving
      ? 'Choisis la case de destination pour déplacer ou échanger l’objet.'
      : 'Choisis une case pour modifier son contenu.'}
  </p>
  <div class="inventory-grid" class:equipment={inv === 'equipment'}>
    {#each inv === 'main' ? [...entries.slice(9), ...entries.slice(0, 9)] : entries as item}<button
        class="inventory-slot"
        class:selected={slot?.slot === item.slot}
        disabled={busy}
        aria-label={`${item.slot} : ${item.name || item.id || 'Vide'}`}
        title={`${item.slot} : ${item.name || item.id || 'Vide'}`}
        onclick={() => chooseSlot(item)}
        >{#if item.id}<img
            src={iconUrl(item.id)}
            alt={item.name || item.id}
          />{#if item.amount > 1}<b>{item.amount}</b>{/if}{:else}<span
            class="slot-empty">·</span
          >{/if}</button
      >{/each}
  </div>
  {#if slot}<div class="slot-editor">
      <strong>{slot.name || slot.id || 'Case vide'}</strong>
      <div class="inline wrap">
        <label
          >Quantité<input
            type="number"
            min="1"
            max="64"
            bind:value={amount}
          /></label
        ><button
          class="secondary align-end"
          disabled={!slot.id || busy}
          onclick={() =>
            act('amount', { slot: slot.slot, rev: slot.rev, count: amount })}
          >Modifier</button
        ><button
          class="secondary align-end"
          onclick={async () => {
            Items = (await import('./Items.svelte')).default;
            choosingItem = true;
          }}>Choisir un objet</button
        ><button
          class="quiet align-end"
          disabled={!slot.id || busy}
          onclick={() => {
            moving = slot;
            slot = null;
          }}>Déplacer</button
        ><button
          class="danger align-end"
          disabled={!slot.id || busy}
          onclick={() => {
            if (confirm('Retirer cet objet de l’inventaire ?'))
              act('clear', { slot: slot.slot, rev: slot.rev });
          }}>Retirer</button
        >
      </div>
    </div>{/if}
{:else if tab === 'objects' && Items}<Items player={name} {toast} />
{:else if tab === 'skins' && Skins}<Skins player={name} {toast} />
{/if}
{#if choosingItem && Items}<div class="modal-backdrop" role="presentation">
    <div
      class="modal wide"
      use:modal={{ close: () => (choosingItem = false) }}
      role="dialog"
      aria-modal="true"
      aria-label="Choisir un objet pour cette case"
      tabindex="-1"
    >
      <div class="modal-heading">
        <h2>Choisir un objet</h2>
        <button
          class="icon-button"
          aria-label="Fermer le catalogue"
          onclick={() => (choosingItem = false)}><Icon name="close" /></button
        >
      </div>
      <Items
        {toast}
        select={async (id) => {
          choosingItem = false;
          await act('set', {
            slot: slot.slot,
            rev: slot.rev,
            item: id,
            count: amount,
          });
        }}
      />
    </div>
  </div>{/if}
