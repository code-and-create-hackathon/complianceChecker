// Options page: upload, list and remove the compliance rules (stored in chrome.storage.local).
import { readComplianceFiles, addWithinLimit } from '@shared/compliance_files.js';
import { COMPLIANCE_MAX_LENGTH } from '@shared/validate.js';
import { getRuleFiles, setRuleFiles, onRuleFilesChanged } from '@/utils/rules_store.js';

const ACCEPTED_FILE = /\.(md|markdown|txt)$/i;
const numberFormat = new Intl.NumberFormat('en-US');
const TOO_LONG = `rules exceed ${numberFormat.format(COMPLIANCE_MAX_LENGTH)} characters in total`;

const $ = (id) => document.getElementById(id);
const fileInput = $('file-input');
const dropzone = $('dropzone');
const message = $('message');
const summary = $('summary');
const status = $('status');
const list = $('file-list');
const empty = $('empty');

function formatSize(bytes) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

function countRules(markdown) {
  return markdown.split(/\r?\n/).filter((line) => /^\s*(?:[-*+]|\d+[.)])\s+\S/.test(line)).length;
}

function showMessage(text) {
  message.textContent = text;
  message.hidden = !text;
}

/** @param {import('@/utils/rules_store.js').StoredFile[]} files */
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
      const rules = countRules(file.text);
      meta.textContent = `${formatSize(file.size)} · ${rules ? `${numberFormat.format(rules)} rules` : 'rule set'}`;
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
  const skipped = [];
  const candidates = picked.filter((file) => {
    if (!ACCEPTED_FILE.test(file.name)) skipped.push(`${file.name} (not .md/.txt)`);
    // UTF-8 needs at most 4 bytes per character: anything bigger is too long for sure.
    else if (file.size > COMPLIANCE_MAX_LENGTH * 4) skipped.push(`${file.name} (${TOO_LONG})`);
    else return true;
    return false;
  });

  const { files, emptyNames } = await readComplianceFiles(candidates);
  emptyNames.forEach((name) => skipped.push(`${name} (empty)`));
  const sizes = new Map(candidates.map((file) => [file.name, file.size]));
  const loaded = files.map((file) => ({ ...file, size: sizes.get(file.name) ?? 0 }));

  const { files: merged, tooLong } = addWithinLimit(await getRuleFiles(), loaded);
  tooLong.forEach((name) => skipped.push(`${name} (${TOO_LONG})`));
  await setRuleFiles(merged);
  render(merged);
  showMessage(skipped.length ? `Not added: ${skipped.join(', ')}.` : '');
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
