# docs/architecture/page/

CSS and JavaScript of `architecture-map.html`. The JavaScript files are fragments of one classic browser
script, not modules: they share each other's functions and the globals the generated page defines
(`MODEL` and the libraries from [`../vendor/`](../vendor/)), so they only make sense joined.
[`generate-architecture-model.sh`](../scripts/generate-architecture-model.sh) joins them in the order of
`order.txt` (one file name per line, without `.js`) after the model JSON; `style.css` goes into the page's
`<style>`.

Handlers written as `onclick="name(...)"` in the generated markup call top-level functions that no code
here calls by name; the lint config reads those names from the script itself, so a new handler needs no
registration while a function nothing calls is still reported.
