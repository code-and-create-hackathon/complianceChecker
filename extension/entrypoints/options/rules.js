// Demo mode (no backend): upload, list and remove the compliance rules (stored in chrome.storage.local).
import { loadComplianceFiles, addWithinLimit, skippedMessage, ruleSummary } from '@shared/compliance_files.js';
import { formatSize } from '@shared/format.js';
import { getRuleFiles, setRuleFiles, onRuleFilesChanged } from '@/utils/rules_store.js';

const $ = (id) => document.getElementById(id);
const fileInput = $('file-input');
const dropzone = $('dropzone');
const message = $('message');
const summary = $('summary');
const status = $('status');
const list = $('file-list');
const empty = $('empty');

function showMessage(text) {
  message.textContent = text;
  message.hidden = !text;
}

/** @param {import('@shared/compliance_files.js').LoadedFile[]} files */
function render(files) {
  list.replaceChildren(
    ...files.map((file) => {
      const item = document.createElement('li');
      const info = document.createElement('div');
      const name = document.createElement('div');
      name.className = 'file-name';
      name.textContent = file.name;
      const meta = document.createElement('div');
      meta.className = 'file-meta';
      meta.textContent = `${formatSize(file.size)} · ${ruleSummary(file.text)}`;
      info.append(name, meta);

      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'link-button';
      remove.textContent = 'Remove';
      remove.setAttribute('aria-label', `Remove ${file.name}`);
      remove.addEventListener('click', () => enqueue(removeFile, file.name));

      item.append(info, remove);
      return item;
    }),
  );
  status.textContent = files.length === 1 ? '1 file loaded' : `${files.length} files loaded`;
  summary.hidden = files.length === 0;
  empty.hidden = files.length > 0;
}

// Storage writes are read-modify-write: run them one after another so fast drops don't overwrite each other.
let queue = Promise.resolve();
function enqueue(task, arg) {
  queue = queue.then(() => task(arg)).catch((error) => showMessage(`Error: ${error.message || error}`));
}

async function addFiles(picked) {
  const { loaded, skipped } = await loadComplianceFiles(picked);
  const { files: merged, skipped: tooLong } = addWithinLimit(await getRuleFiles(), loaded);
  await setRuleFiles(merged);
  render(merged);
  showMessage(skippedMessage([...skipped, ...tooLong]));
}

async function removeFile(name) {
  const files = (await getRuleFiles()).filter((f) => f.name !== name);
  await setRuleFiles(files);
  render(files);
  showMessage('');
}

async function clearAll() {
  await setRuleFiles([]);
  render([]);
  showMessage('');
}

export function initRules() {
  $('rules-section').hidden = false;

  fileInput.addEventListener('change', () => {
    const picked = [...fileInput.files];
    fileInput.value = '';
    if (picked.length) enqueue(addFiles, picked);
  });

  dropzone.addEventListener('dragover', (event) => {
    event.preventDefault();
    dropzone.classList.add('is-dragover');
  });
  dropzone.addEventListener('dragleave', (event) => {
    if (!dropzone.contains(event.relatedTarget)) dropzone.classList.remove('is-dragover');
  });
  dropzone.addEventListener('drop', (event) => {
    event.preventDefault();
    dropzone.classList.remove('is-dragover');
    const picked = [...event.dataTransfer.files];
    if (picked.length) enqueue(addFiles, picked);
  });
  // Stops the browser from opening a file dropped outside the dropzone.
  ['dragover', 'drop'].forEach((type) => window.addEventListener(type, (event) => event.preventDefault()));

  $('clear').addEventListener('click', () => enqueue(clearAll));

  onRuleFilesChanged(render); // keeps several open options tabs in sync
  getRuleFiles().then(render);
}
