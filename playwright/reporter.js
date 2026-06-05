const fs = require('fs');

class ProgressReporter {
  constructor(options) {
    this.file = (options && options.outputFile) || '/tmp/pw-live.log';
    this.count = 0;
    this.start = Date.now();
    fs.writeFileSync(this.file, '');
  }

  onTestEnd(test, result) {
    this.count++;
    const icon = result.status === 'passed' ? '✓' : result.status === 'skipped' ? '-' : '×';
    const duration = (result.duration / 1000).toFixed(1);
    const title = test.titlePath().slice(1).join(' › ');
    const file = test.location.file.replace('/tmp/', '');
    const line = `${icon}  ${this.count} ${file} › ${title} (${duration}s)\n`;
    fs.appendFileSync(this.file, line);
  }

  onEnd(result) {
    const total = ((Date.now() - this.start) / 1000).toFixed(1);
    const summary = result.status === 'passed'
      ? `${this.count} passed (${total}s)\n`
      : `FAILED — ${this.count} tests (${total}s)\n`;
    fs.appendFileSync(this.file, summary);
  }
}

module.exports = ProgressReporter;
