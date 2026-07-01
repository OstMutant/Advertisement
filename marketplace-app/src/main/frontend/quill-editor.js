import Quill from 'quill';
import 'quill/dist/quill.snow.css';

class QuillEditorElement extends HTMLElement {
  constructor() {
    super();
    this.__value = '';
    this.__quill = null;
    this.__labelEl = null;
  }

  static get observedAttributes() {
    return ['label'];
  }

  attributeChangedCallback(name, _oldVal, newVal) {
    if (name === 'label' && this.__labelEl) {
      this.__labelEl.textContent = newVal || '';
    }
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
      this.__quill.root.innerHTML = this.__value;
    }

    this.__quill.on('text-change', () => {
      const html = this.__quill.getSemanticHTML();
      this.__value = html === '<p><br></p>' ? '' : html;
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
        this.__quill.root.innerHTML = this.__value || '';
      }
    }
  }
}

customElements.define('quill-editor', QuillEditorElement);
