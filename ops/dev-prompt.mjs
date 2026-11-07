// Terminal soru-cevap yardımcıları (dev.mjs ve dev-mail.mjs ortak kullanır).

/** Bir satır okur. */
export function ask(question) {
  process.stdout.write(question);
  return new Promise((done) => {
    process.stdin.once("data", (chunk) => {
      process.stdin.pause();
      done(String(chunk).trim());
    });
  });
}

/** Terminalde yazılanı göstermeden okur (karakterler ekrana yansımaz). İptal (Ctrl+C) hata fırlatır. */
export function askHidden(question) {
  process.stdout.write(question);
  return new Promise((done, reject) => {
    const stdin = process.stdin;
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding("utf8");
    let value = "";
    const finish = () => {
      stdin.setRawMode(false);
      stdin.pause();
      stdin.off("data", onData);
    };
    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === "\u0003") {
          finish();
          reject(new Error("İptal edildi."));
          return;
        }
        if (char === "\r" || char === "\n") {
          finish();
          process.stdout.write("\n");
          done(value);
          return;
        }
        if (char === "\u007f" || char === "\b") value = value.slice(0, -1);
        else value += char;
      }
    };
    stdin.on("data", onData);
  });
}
