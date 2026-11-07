import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { forgetFragmentToken, fragmentTokenStore, readFragmentToken } from "./fragment-token";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdEf";

function setUrl(path: string) {
  window.history.replaceState(null, "", path);
}

beforeEach(() => {
  forgetFragmentToken();
  setUrl("/");
});
afterEach(() => {
  forgetFragmentToken();
  setUrl("/");
});

describe("readFragmentToken", () => {
  it("URL parçasındaki token'ı okur ve adres çubuğundan siler (yol ve sorgu korunur)", () => {
    setUrl(`/auth/reset-password?lang=tr#token=${TOKEN}`);

    expect(readFragmentToken()).toBe(TOKEN);

    expect(window.location.hash).toBe("");
    expect(window.location.pathname + window.location.search).toBe("/auth/reset-password?lang=tr");
  });

  it("okunduktan sonra bellekte kalır (adres temizlendiği hâlde ikinci okuma aynı token'ı verir)", () => {
    setUrl(`/x#token=${TOKEN}`);
    readFragmentToken();

    expect(readFragmentToken()).toBe(TOKEN);
  });

  it("token yoksa null verir", () => {
    setUrl("/auth/reset-password");
    expect(readFragmentToken()).toBeNull();
  });

  it.each([
    ["kısa token", "#token=abc"],
    ["geçersiz karakter", `#token=${TOKEN}!!`],
    ["başka parça adı", `#t=${TOKEN}`],
    ["sorgu gibi", `#token=${TOKEN}&x=1`],
    ["boş", "#token="]
  ])("%s kabul edilmez ve parça adreste bırakılmaz değil, token null olur", (_ad, hash) => {
    setUrl(`/x${hash}`);
    expect(readFragmentToken()).toBeNull();
  });

  it("unutulunca yeniden okunabilir hâle gelir", () => {
    setUrl(`/x#token=${TOKEN}`);
    readFragmentToken();
    forgetFragmentToken();
    setUrl("/x");
    expect(readFragmentToken()).toBeNull();
  });

  it("token localStorage/sessionStorage'a yazılmaz", () => {
    setUrl(`/x#token=${TOKEN}`);
    readFragmentToken();
    expect(JSON.stringify({ ...window.localStorage, ...window.sessionStorage })).not.toContain(TOKEN);
  });
});

describe("fragmentTokenStore", () => {
  it("sunucu görüntüsü undefined'dır (hidrasyon uyuşmazlığı olmaz)", () => {
    expect(fragmentTokenStore.getServerSnapshot()).toBeUndefined();
  });
});
