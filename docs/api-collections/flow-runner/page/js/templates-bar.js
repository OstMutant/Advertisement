import {
  builtIns,
  deleteTemplate,
  fromConfig,
  loadSaved,
  matchingName,
  saveTemplate,
  toConfig,
  validateConfig,
} from "./templates.js";

export function createTemplatesBar(container, collection, selection, storage, { guard }) {
  const builtin = builtIns(collection);
  const current = element("span");
  current.id = "template-label";
  const line = element("p", "template-line");
  line.append("Template: ", current);
  const buttons = element("div", "template-buttons");
  buttons.setAttribute("role", "tablist");

  const textarea = element("textarea");
  textarea.id = "template-config";
  textarea.spellcheck = false;
  textarea.setAttribute("aria-label", "Run configuration as JSON");
  const message = element("span", "msg");
  message.setAttribute("role", "status");

  const copy = element("button", "", "Copy");
  copy.type = "button";
  copy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(textarea.value);
      message.textContent = "Copied.";
    } catch {
      textarea.select();
      message.textContent = "Copy is not available here. The text is selected, press Ctrl+C.";
    }
    message.className = "msg";
  });

  const apply = element("button", "primary", "Apply configuration");
  apply.id = "template-apply";
  apply.type = "button";
  apply.addEventListener("click", () => {
    const result = validateConfig(textarea.value, collection);
    if (result.error !== undefined) {
      message.textContent = `${result.error}. Previous configuration is still in use.`;
      message.className = "msg bad";
      return;
    }
    message.textContent = "";
    message.className = "msg";
    guard(() => selection.load(fromConfig(result.config, collection)), () => {});
  });

  const actions = element("div", "template-actions");
  actions.append(copy, apply, message);

  const nameInput = element("input");
  nameInput.id = "template-name";
  nameInput.type = "text";
  nameInput.setAttribute("aria-label", "Template name");
  nameInput.placeholder = "Template name";
  const save = element("button", "", "Save as");
  save.id = "template-save";
  save.type = "button";
  save.addEventListener("click", () => {
    const result = saveTemplate(storage, collection, nameInput.value, toConfig(selection.state(), collection));
    if (result.error !== undefined) {
      message.textContent = result.error;
      message.className = "msg bad";
      return;
    }
    message.textContent = `Saved "${nameInput.value.trim()}".`;
    message.className = "msg";
    nameInput.value = "";
    refresh();
  });
  const saving = element("div", "template-saving");
  saving.hidden = storage === null;
  saving.append(nameInput, save);

  container.append(line, buttons, textarea, actions, saving);

  function refresh() {
    const saved = storage === null ? {} : loadSaved(storage, collection);
    const config = toConfig(selection.state(), collection);
    const name = matchingName(config, saved, collection);
    current.textContent = name;
    buttons.replaceChildren();
    const all = { ...builtin, ...saved };
    for (const templateName of Object.keys(all)) {
      const group = element("span", "template");
      group.setAttribute("role", "presentation");
      const button = element("button", "", templateName);
      button.type = "button";
      button.dataset.template = templateName;
      button.setAttribute("role", "tab");
      button.setAttribute("aria-selected", String(templateName === name));
      button.addEventListener("click", () => guard(() => selection.load(fromConfig(all[templateName], collection)), () => {}));
      group.append(button);
      if (Object.hasOwn(saved, templateName)) {
        const remove = element("button", "delete", "x");
        remove.type = "button";
        remove.setAttribute("aria-label", `Delete ${templateName}`);
        remove.addEventListener("click", () => {
          deleteTemplate(storage, collection, templateName);
          refresh();
        });
        group.append(remove);
      }
      buttons.append(group);
    }
    textarea.value = JSON.stringify(config, null, 2);
  }

  selection.subscribe(refresh);
  refresh();
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}
