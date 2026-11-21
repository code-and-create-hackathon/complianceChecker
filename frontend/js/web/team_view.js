// Administrator only: list the team and add workers. There is no self-registration.
import { apiFetch } from "../shared/api.js";
import { getToken } from "./company_accounts.js";
import { $, announce, setFieldError } from "./dom.js";

const team = $("team");
const form = $("user-form");
const nameInput = $("user-name");
const userError = $("user-error");
const userList = $("user-list");

const USERNAME = /^[a-z0-9._-]{2,40}$/;

/** @param {{username: string, role: string}[]} users */
function renderUsers(users) {
  userList.replaceChildren(
    ...users.map((user) => {
      const item = document.createElement("li");
      item.className = "user-item";
      const name = document.createElement("span");
      name.textContent = user.username;
      const role = document.createElement("span");
      role.className = "badge";
      role.textContent = user.role === "admin" ? "Administrator" : "Worker";
      item.append(name, role);
      return item;
    }),
  );
}

async function loadUsers() {
  try {
    renderUsers(await apiFetch("/api/users", { token: getToken() }));
  } catch (error) {
    setFieldError(nameInput, userError, `The team could not be loaded: ${error.message}`);
  }
}

async function addUser(event) {
  event.preventDefault();
  const username = nameInput.value.trim().toLowerCase();
  if (!USERNAME.test(username)) {
    setFieldError(nameInput, userError, "2–40 characters: lower-case letters, digits, '.', '_' or '-'.");
    nameInput.focus();
    return;
  }
  setFieldError(nameInput, userError, "");
  try {
    await apiFetch("/api/users", { method: "POST", body: { username }, token: getToken() });
    nameInput.value = "";
    announce(`${username} can now log in.`);
    await loadUsers();
  } catch (error) {
    setFieldError(nameInput, userError, error.message);
  }
  nameInput.focus();
}

/** @param {boolean} visible  true for the administrator */
export function showTeam(visible) {
  team.hidden = !visible;
  if (visible) loadUsers();
}

export function initTeamView() {
  form.addEventListener("submit", addUser);
}
