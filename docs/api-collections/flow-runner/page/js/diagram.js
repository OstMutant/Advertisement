const NODE_WIDTH = 150;
const NODE_HEIGHT = 44;
const GAP = 36;
const PADDING = 12;
const SVG_NS = "http://www.w3.org/2000/svg";

export function createDiagram(container) {
  const nodes = [];
  let svg = null;

  function createSvg() {
    svg = svgElement("svg", { class: "flow-diagram", height: NODE_HEIGHT + 2 * PADDING });
    const defs = svgElement("defs");
    const marker = svgElement("marker", {
      id: "flow-arrow",
      markerWidth: 8,
      markerHeight: 8,
      refX: 7,
      refY: 4,
      orient: "auto",
    });
    marker.append(svgElement("path", { d: "M0,0 L8,4 L0,8 z", class: "arrow-head" }));
    defs.append(marker);
    svg.append(defs);
    container.append(svg);
  }

  function addNode() {
    const position = nodes.length;
    const x = PADDING + position * (NODE_WIDTH + GAP);
    const centerY = PADDING + NODE_HEIGHT / 2;
    if (position > 0) {
      svg.append(
        svgElement("path", {
          class: "link",
          "marker-end": "url(#flow-arrow)",
          d: `M${x - GAP + 2},${centerY} L${x - 2},${centerY}`,
        }),
      );
    }
    const index = svgElement("text", { class: "index", x: x + 10, y: PADDING + 16 });
    index.textContent = String(position + 1);
    const g = svgElement("g", { class: "step pending" });
    g.append(
      svgElement("rect", { width: NODE_WIDTH, height: NODE_HEIGHT, rx: 6, x, y: PADDING }),
      index,
      svgElement("text", { class: "name", x: x + 10, y: PADDING + 33 }),
      svgElement("title"),
    );
    nodes.push(g);
    svg.append(g);

    const width = PADDING * 2 + nodes.length * NODE_WIDTH + (nodes.length - 1) * GAP;
    svg.setAttribute("width", width);
    svg.setAttribute("viewBox", `0 0 ${width} ${NODE_HEIGHT + 2 * PADDING}`);
  }

  function update(index, step) {
    if (svg === null) {
      createSvg();
    }
    while (nodes.length <= index) {
      addNode();
    }

    const node = nodes[index];
    node.dataset.step = step.name;
    node.setAttribute("class", `step ${step.status}`);
    node.querySelector("text.name").textContent = step.name;
    const codeSuffix = step.response.code !== null ? ` (${step.response.code})` : "";
    node.querySelector("title").textContent = `${step.name}: ${step.status}${codeSuffix}`;
  }

  function reset() {
    container.replaceChildren();
    nodes.length = 0;
    svg = null;
  }

  return { update, reset };
}

function svgElement(tag, attributes = {}) {
  const element = document.createElementNS(SVG_NS, tag);
  for (const [name, value] of Object.entries(attributes)) {
    element.setAttribute(name, value);
  }
  return element;
}
