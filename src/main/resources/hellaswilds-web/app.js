(function () {
  const status = document.getElementById('status');
  const zoneInfo = document.getElementById('zone');
  const tableBody = document.querySelector('#rules tbody');
  const addButton = document.getElementById('add');
  const saveButton = document.getElementById('save');

  const params = new URLSearchParams(window.location.search);
  const token = params.get('auth');

  if (!token) {
    status.textContent = 'Missing auth token. Launch the editor from /hellas wilds spawns edit.';
    addButton.disabled = true;
    saveButton.disabled = true;
    return;
  }

  let currentRules = [];

  async function load() {
    try {
      status.textContent = 'Loading zone data…';
      const response = await fetch(`/api/spawns?auth=${encodeURIComponent(token)}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      zoneInfo.textContent = `Zone ${payload.zone.displayNumber} · mode ${payload.zone.spawnMode} · cap ${payload.zone.spawnCap}`;
      currentRules = payload.rules || [];
      renderTable();
      status.textContent = 'Ready.';
    } catch (error) {
      console.error(error);
      status.textContent = `Failed to load zone data: ${error.message}`;
    }
  }

  function renderTable() {
    tableBody.innerHTML = '';
    currentRules.forEach((rule) => {
      tableBody.appendChild(buildRow(rule));
    });
    if (currentRules.length === 0) {
      tableBody.appendChild(buildRow({}));
    }
  }

  function buildRow(rule) {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td><input data-field="species" value="${rule.species ?? ''}"></td>
      <td><input type="number" data-field="levelMin" value="${rule.levelMin ?? 5}" min="1"></td>
      <td><input type="number" data-field="levelMax" value="${rule.levelMax ?? 10}" min="1"></td>
      <td><input type="number" step="0.1" data-field="weight" value="${rule.weight ?? 1}"></td>
      <td><input data-field="time" value="${(rule.time || []).join(', ')}"></td>
      <td><input data-field="weather" value="${(rule.weather || []).join(', ')}"></td>
      <td><input data-field="form" value="${rule.form ?? ''}"></td>
      <td><input data-field="size" value="${rule.size ?? ''}"></td>
      <td><input data-field="ribbons" value="${(rule.ribbons || []).join(', ')}"></td>
      <td><input type="checkbox" data-field="alphaRibbon" ${rule.alphaRibbon ? 'checked' : ''}></td>
      <td><input type="checkbox" data-field="softDespawn" ${rule.softDespawn ? 'checked' : ''}></td>
      <td><input type="number" data-field="cooldownSeconds" value="${rule.cooldownSeconds ?? ''}" min="0"></td>
    `;
    return row;
  }

  function normaliseList(value) {
    if (!value) {
      return [];
    }
    return value.split(',').map((entry) => entry.trim()).filter(Boolean);
  }

  function collectRules() {
    const rows = Array.from(tableBody.querySelectorAll('tr'));
    return rows.map((row) => {
      const obj = {};
      Array.from(row.querySelectorAll('input')).forEach((input) => {
        const field = input.dataset.field;
        if (!field) {
          return;
        }
        switch (field) {
          case 'species':
          case 'form':
          case 'size':
            obj[field] = input.value.trim();
            break;
          case 'levelMin':
          case 'levelMax':
            obj[field] = parseInt(input.value, 10) || 1;
            break;
          case 'weight':
            obj[field] = parseFloat(input.value) || 1;
            break;
          case 'time':
          case 'weather':
          case 'ribbons':
            obj[field] = normaliseList(input.value);
            break;
          case 'alphaRibbon':
          case 'softDespawn':
            obj[field] = input.checked;
            break;
          case 'cooldownSeconds':
            obj[field] = input.value === '' ? null : parseInt(input.value, 10);
            break;
          default:
            break;
        }
      });
      return obj;
    }).filter((rule) => rule.species && rule.species.length > 0);
  }

  addButton.addEventListener('click', () => {
    tableBody.appendChild(buildRow({}));
  });

  saveButton.addEventListener('click', async () => {
    try {
      status.textContent = 'Saving…';
      const payload = { rules: collectRules() };
      const response = await fetch(`/api/spawns?auth=${encodeURIComponent(token)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      currentRules = data.rules || [];
      renderTable();
      status.textContent = 'Saved changes.';
    } catch (error) {
      console.error(error);
      status.textContent = `Failed to save: ${error.message}`;
    }
  });

  load();
})();
