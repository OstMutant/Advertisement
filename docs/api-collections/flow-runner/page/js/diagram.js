const NODE_WIDTH = 190;
const NODE_HEIGHT = 44;
const GAP = 36;
const PADDING = 12;
const ROW_PITCH = 92;
const LABEL_OFFSET = 26;
const SVG_NS = "http://www.w3.org/2000/svg";

export function createDiagram(container) {
  const nodes = [];
  const rows = [];
  let svg = null;

  function createSvg() {
    svg = svgElement("svg", { class: "flow-diagram" });
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

  function addNode(index, step) {
    if (rows.length === 0 || rows[rows.length - 1].flow !== step.flow) {
      rows.push({ flow: step.flow, count: 0 });
      if (step.flow !== "") {
        const label = svgElement("text", {
          class: "flow-name",
          x: PADDING,
          y: (rows.length - 1) * ROW_PITCH + 18,
        });
        label.textContent = step.flow;
        svg.append(label);
      }
    }
    const row = rows.length - 1;
    const col = rows[row].count++;
    const x = PADDING + col * (NODE_WIDTH + GAP);
    const nodeY = row * ROW_PITCH + LABEL_OFFSET;
    if (col > 0) {
      svg.append(
        svgElement("path", {
          class: "link",
          "marker-end": "url(#flow-arrow)",
          d: `M${x - GAP + 2},${nodeY + NODE_HEIGHT / 2} L${x - 2},${nodeY + NODE_HEIGHT / 2}`,
        }),
      );
    }
    const indexText = svgElement("text", { class: "index", x: x + 10, y: nodeY + 16 });
    indexText.textContent = String(index + 1);
    const g = svgElement("g", { class: "step pending" });
    g.append(
      svgElement("rect", { width: NODE_WIDTH, height: NODE_HEIGHT, rx: 6, x, y: nodeY }),
      indexText,
      svgElement("text", { class: "name", x: x + 10, y: nodeY + 33 }),
      svgElement("title"),
    );
    nodes.push(g);
    svg.append(g);
    resize();
  }

  function resize() {
    const maxCols = rows.reduce((max, row) => Math.max(max, row.count), 0);
    const width = PADDING * 2 + maxCols * NODE_WIDTH + (maxCols - 1) * GAP;
    const height = rows.length * ROW_PITCH + 8;
    svg.setAttribute("width", width);
    svg.setAttribute("height", height);
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  }

  function update(index, step) {
    if (index > nodes.length) {
      throw new Error("diagram steps must be reported in order");
    }
    if (index === nodes.length) {
      if (svg === null) {
        createSvg();
      }
      addNode(index, step);
    }

    const node = nodes[index];
    node.dataset.step = step.id;
    node.setAttribute("class", `step ${step.status}`);
    node.querySelector("text.name").textContent = step.name;
    const codeSuffix = step.response.code !== null ? ` (${step.response.code})` : "";
    node.querySelector("title").textContent = `${step.name}: ${step.status}${codeSuffix}`;
  }

  function reset() {
    container.replaceChildren();
    nodes.length = 0;
    rows.length = 0;
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
