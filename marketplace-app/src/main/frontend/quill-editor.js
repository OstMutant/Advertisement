import Quill from 'quill';
import 'quill/dist/quill.snow.css';

class QuillEditorElement extends HTMLElement {
  constructor() {
    super();
    this.__value = '';
    this.__quill = null;
    this.__labelEl = null;
    this.__counterEl = null;
    this.__maxLength = null;
  }

  static get observedAttributes() {
    return ['label', 'maxlength'];
  }

  attributeChangedCallback(name, _oldVal, newVal) {
    if (name === 'label' && this.__labelEl) {
      this.__labelEl.textContent = newVal || '';
    }
    if (name === 'maxlength') {
      this.__maxLength = newVal ? parseInt(newVal, 10) : null;
      this.__updateCounter();
    }
  }

  __updateCounter() {
    if (!this.__counterEl) return;
    if (this.__maxLength == null) {
      this.__counterEl.textContent = '';
      return;
    }
    const text = this.__quill ? this.__quill.getText() : '\n';
    const length = Math.max(0, text.length - 1);
    this.__counterEl.textContent = `${length} / ${this.__maxLength}`;
  }

  connectedCallback() {
    if (this.__quill) return;

    this.__labelEl = document.createElement('label');
    this.__labelEl.className = 'quill-editor-label';
    this.__labelEl.textContent = this.getAttribute('label') || '';
    this.appendChild(this.__labelEl);

    const container = document.createElement('div');
    this.appendChild(container);

    this.__quill = new Quill(container, {
      theme: 'snow',
      modules: {
        toolbar: [
          ['bold', 'italic', 'underline', 'strike'],
          ['blockquote'],
          [{ list: 'ordered' }, { list: 'bullet' }],
          [{ header: [1, 2, 3, false] }],
          ['link'],
          ['clean'],
        ],
      },
    });

    if (this.__value) {
      const delta = this.__quill.clipboard.convert({ html: this.__value });
      this.__quill.setContents(delta, 'silent');
    }

    this.__counterEl = document.createElement('div');
    this.__counterEl.className = 'quill-editor-counter';
    this.appendChild(this.__counterEl);
    this.__maxLength = this.hasAttribute('maxlength') ? parseInt(this.getAttribute('maxlength'), 10) : null;
    this.__updateCounter();

    // Our own hydration writes use source 'silent' (see above / set value()), which never
    // reaches this listener at all — so anything that does arrive here is a genuine change
    // (real typing, toolbar clicks, or an external API call), not an echo. No source filter
    // needed: filtering to 'user' only would incorrectly ignore legitimate 'api'-sourced
    // content changes (e.g. Quill.setContents(delta) called without an explicit source).
    this.__quill.on('text-change', () => {
      const html = this.__quill.getSemanticHTML();
      this.__value = html === '<p><br></p>' ? '' : html;
      this.__updateCounter();
      this.dispatchEvent(new CustomEvent('value-changed', {
        detail: { value: this.__value },
        bubbles: true,
      }));
    });
  }

  get value() {
    return this.__value;
  }

  set value(newVal) {
    this.__value = newVal || '';
    if (this.__quill) {
      const current = this.__quill.getSemanticHTML();
      const currentNormalized = current === '<p><br></p>' ? '' : current;
      if (currentNormalized !== this.__value) {
        const delta = this.__quill.clipboard.convert({ html: this.__value || '' });
        this.__quill.setContents(delta, 'silent');
      }
    }
  }
}

customElements.define('quill-editor', QuillEditorElement);
