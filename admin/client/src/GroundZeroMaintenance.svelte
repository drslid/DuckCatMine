<script lang="ts">
  import Icon from './Icon.svelte';
  let { status, busy, report, act, ask, openMaintenance }: any = $props();
  let radius = $state(640),
    slice = $state<number | undefined>(),
    upgradeRadius = $state(96),
    world = $state('');
  let renovateX = $state(0),
    renovateZ = $state(0),
    renovateRadius = $state(128);
  let unavailable = $derived(!status.world);
  let renovationBlocked = $derived(!!status.survivors?.length && status.renovationBlocked !== false);
</script>

<div class="maintenance-grid">
  <article class="card">
    <div class="card-heading">
      <h2>Vérifier la partie</h2>
      <Icon name="shield" />
    </div>
    <dl class="key-values">
      <dt>Base de données</dt>
      <dd>{status.database === false ? 'En erreur' : 'Opérationnelle'}</dd>
      <dt>Inventaires classiques conservés</dt>
      <dd>{status.classicProfilesHeld ?? 0}</dd>
      <dt>Monde</dt>
      <dd>{status.world || 'Absent'}</dd>
      <dt>Génération</dt>
      <dd>{status.epoch ?? '—'}</dd>
    </dl>
    <div class="actions">
      <button
        class="secondary"
        disabled={!!busy || status.stale}
        onclick={() => act('status')}><Icon name="search" />Diagnostic</button
      >
      <button
        class="secondary"
        disabled={!!busy || status.stale}
        onclick={() => act('selftest')}><Icon name="check" />Autotest</button
      >
      <button
        class="quiet"
        disabled={!!busy || status.stale}
        onclick={() => act('reload')}
        ><Icon name="refresh" />Recharger les réglages</button
      >
    </div>
    <p class="muted small">
      L’autotest crée puis retire des objets et des entités de test. Lance-le de
      préférence hors d’une session chargée.
    </p>
    {#if report.length}<pre class="report" aria-live="polite">{report.join(
          '\n',
        )}</pre>{/if}
    <button class="quiet guide-link" onclick={openMaintenance}
      ><Icon name="save" size={16} />Sauvegardes et relance du serveur</button
    >
  </article>

  <article class="card">
    <div class="card-heading">
      <h2>Carte et rues</h2>
      <Icon name="map" />
    </div>
    <p class="muted">
      Le terrain est une image ; les positions des survivants restent
      actualisées. Un grand rendu peut augmenter la charge.
    </p>
    <form
      onsubmit={(e) => {
        e.preventDefault();
        ask({
          action: 'map',
          value: { radius, ...(slice == null ? {} : { slice }) },
          title: 'Redessiner la carte',
          description: `Rendre une zone de ${radius * 2} × ${radius * 2} blocs. Le calcul continue en arrière-plan ; privilégie un moment calme.`,
        });
      }}
    >
      <div class="form-grid">
        <label
          >Zone<select bind:value={radius}
            ><option value={320}>Centre · 640 blocs</option><option value={640}
              >Ville · 1 280 blocs</option
            ><option value={960}>Ville et abords</option><option value={1280}
              >Grande carte</option
            ><option value={1536}>Monde entier · 3 072 blocs</option></select
          ></label
        >
        <label
          >Coupe en hauteur (facultatif)<input
            type="number"
            min="-2048"
            max="2048"
            placeholder="Vue de surface"
            bind:value={slice}
          /></label
        >
      </div>
      <button class="secondary" disabled={!!busy || status.stale || unavailable}
        ><Icon name="refresh" />Redessiner le terrain</button
      >
    </form>
    <details class="advanced">
      <summary>Actualiser les rues existantes</summary>
      <p class="muted small">
        Ajoute les voitures et arbres prévus par le générateur aux tronçons
        autour du refuge. Cette action modifie les rues.
      </p>
      <form
        class="inline"
        onsubmit={(e) => {
          e.preventDefault();
          ask({
            action: 'upgrade',
            value: upgradeRadius,
            title: 'Actualiser les rues',
            description: `Mettre à niveau les tronçons dans un rayon de ${upgradeRadius} blocs autour du refuge. Crée une sauvegarde avant si vous avez construit dans ces rues.`,
            required: 'ACTUALISER',
          });
        }}
      >
        <label
          >Rayon<input
            type="number"
            min="16"
            max="512"
            bind:value={upgradeRadius}
            required
          /></label
        ><button
          class="secondary"
          disabled={!!busy || status.stale || unavailable}>Actualiser</button
        >
      </form>
    </details>
  </article>

  <article class="card">
    <div class="card-heading">
      <h2>Rénover les bâtiments</h2>
      <Icon name="build" />
    </div>
    <p class="muted small">
      Maisons meublées, escaliers et rayons à fouiller. La rénovation conserve
      les ajouts, les bases revendiquées et les conteneurs. Les nouvelles zones
      utilisent déjà les nouveaux modèles.
    </p>
    {#if status.renovationExclusions?.length}<details class="advanced"><summary>Quartiers préservés · {status.renovationExclusions.length}</summary>{#each status.renovationExclusions as area}<p class="muted small"><strong>{area.label}</strong> · X {area.minX} à {area.maxX} · Z {area.minZ} à {area.maxZ} · toutes les hauteurs. Aucun travail dans cette zone ni sur les approches qui la touchent.</p>{/each}</details>{/if}
    {#if renovationBlocked}<p class="notice">
        Les joueurs doivent rejoindre un quartier préservé ou quitter Ground Zero avant la rénovation.
      </p>{/if}
    {#if status.renovation}<p class="notice" aria-live="polite">
        {status.renovation.message}<br />{status.renovation.buildings} bâtiments ·
        {status.renovation.changed} blocs améliorés · {status.renovation
          .preserved} conservés · {status.renovation.skipped} ignorés
      </p>{/if}
    <form
      onsubmit={(e) => {
        e.preventDefault();
        ask({
          action: 'renovate',
          value: { x: renovateX, z: renovateZ, radius: renovateRadius },
          title: 'Rénover cette zone',
          description: `Améliorer les bâtiments existants dans un rayon de ${renovateRadius} blocs autour de ${renovateX} · ${renovateZ}. Les quartiers préservés sont exclus. Le travail attend si un joueur est ailleurs dans Ground Zero. Prévois une sauvegarde avant la première rénovation.`,
          required: 'RENOVER',
        });
      }}
    >
      <div class="form-grid">
        <label>X<input type="number" bind:value={renovateX} required /></label
        ><label>Z<input type="number" bind:value={renovateZ} required /></label
        ><label
          >Rayon<input
            type="number"
            min="16"
            max="768"
            bind:value={renovateRadius}
            required
          /></label
        >
      </div>
      <button
        class="secondary"
        disabled={!!busy ||
          status.stale ||
          unavailable ||
          !status.capabilities?.includes('city-renovation') ||
          renovationBlocked ||
          ['running', 'paused'].includes(status.renovation?.state)}
        ><Icon name="build" />Rénover la zone</button
      >
    </form>
  </article>

  <article class="card wide">
    <div class="card-heading">
      <h2>Gestion du monde</h2>
      <Icon name="build" />
    </div>
    <p class="muted small">
      Ces commandes gèrent un monde dédié. Pour une restauration ou un
      redémarrage, utilise la maintenance du serveur.
    </p>
    <details class="advanced">
      <summary>Créer ou associer un monde</summary>
      <p class="muted small">
        Disponible quand aucun monde n’est déjà associé. L’association change
        les règles du monde choisi ; il doit être chargé et dédié à Ground Zero.
      </p>
      <div class="actions">
        <button
          class="secondary"
          disabled={!!busy || status.stale || !unavailable}
          onclick={() =>
            ask({
              action: 'world-create',
              title: 'Créer Ground Zero',
              description:
                'Générer un nouveau monde dédié et son refuge. Le scénario restera fermé jusqu’à son ouverture.',
              required: 'CREER',
            })}>Créer le monde dédié</button
        >
      </div>
      <form
        class="inline"
        onsubmit={(e) => {
          e.preventDefault();
          ask({
            action: 'world-bind',
            value: world,
            title: 'Associer un monde',
            description: `Associer ${world} à Ground Zero et y appliquer les règles du scénario. Ce monde doit être chargé et réservé à ce mode.`,
            required: world,
          });
        }}
      >
        <label
          >Nom du monde chargé<input
            bind:value={world}
            pattern={'[A-Za-z0-9_-]{1,48}'}
            placeholder="monde_dedie"
            required
          /></label
        ><button
          class="secondary"
          disabled={!!busy || status.stale || !unavailable}>Associer</button
        >
      </form>
    </details>
    <details class="advanced">
      <summary>Récupérer un monde absent</summary>
      <p class="muted small">
        Recrée le monde associé si son dossier a déjà été mis de côté pendant un
        arrêt du serveur. Les profils sont conservés et les survivants
        reprendront au refuge. Disponible seulement si le monde associé est
        absent.
      </p>
      <button
        class="secondary"
        disabled={!!busy || status.stale || !unavailable || !status.problem}
        onclick={() =>
          ask({
            action: 'world-recreate',
            title: 'Recréer le monde absent',
            description:
              'Générer un nouveau terrain pour le monde associé devenu absent. Les constructions de l’ancienne génération restent dans l’ancien dossier, à conserver avec la sauvegarde.',
            required: 'RECREER',
          })}>Recréer le monde absent</button
      >
    </details>
  </article>

  <article class="card wide danger-zone">
    <div class="card-heading">
      <h2>Nouvelle partie</h2>
      <Icon name="skull" />
    </div>
    <p class="muted">
      Termine les vies Ground Zero : inventaires du scénario, progression des
      vies en cours et sacs sont remis à zéro. Le terrain, les constructions et
      les inventaires classiques sont conservés.
    </p>
    {#if status.survivors?.length}<p class="notice">
        {status.survivors.length} survivant(s) encore présents. Fais-les sortir avant
        de recommencer.
      </p>{/if}
    <button
      class="danger"
      disabled={!!busy || status.stale || !!status.survivors?.length}
      onclick={() =>
        ask({
          action: 'lives-reset',
          title: 'Recommencer la partie',
          description:
            'Toutes les vies encore actives seront terminées, sans sac. Chacun repartira de zéro dans Ground Zero. Crée d’abord une sauvegarde si tu souhaites pouvoir revenir à cette partie.',
          required: 'RECOMMENCER',
          danger: true,
        })}>Recommencer les vies</button
    >
  </article>
</div>

<style>
  .maintenance-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
  .wide {
    grid-column: 1/-1;
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin: 14px 0;
  }
  .small {
    font-size: 11px;
    line-height: 1.6;
  }
  .report {
    font-family: inherit;
    font-size: 11px;
    line-height: 1.7;
    padding: 12px;
    background: var(--surface2);
    border-radius: 10px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  .advanced {
    border-top: 1px solid var(--border);
    margin-top: 15px;
    padding-top: 14px;
  }
  .advanced summary {
    cursor: pointer;
    font-size: 12px;
    font-weight: 600;
  }
  .inline {
    align-items: end;
    flex-wrap: wrap;
    margin: 12px 0;
  }
  .inline label {
    min-width: 0;
    flex: 1;
  }
  input,
  select {
    width: 100%;
    min-width: 0;
  }
  .form-grid {
    margin: 14px 0;
  }
  .guide-link {
    display: inline-flex;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    color: var(--accent);
    margin-top: 12px;
  }
  .danger-zone {
    border-color: #805446;
  }
  .notice {
    background: #895b3522;
    color: var(--soft);
    font-size: 12px;
    padding: 10px;
    border-radius: 9px;
  }
  @media (max-width: 850px) {
    .maintenance-grid {
      grid-template-columns: minmax(0, 1fr);
    }
    .wide {
      grid-column: auto;
    }
  }
  @media (max-width: 550px) {
    input,
    select {
      font-size: 16px;
    }
    .inline {
      align-items: stretch;
    }
    .inline label,
    .inline button {
      flex-basis: 100%;
    }
    .form-grid {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
