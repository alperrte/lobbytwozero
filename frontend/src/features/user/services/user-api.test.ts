import { describe, expect, it, vi } from "vitest";
import { getMyProfileSummary } from "./user-api";

const request = (body: unknown) => vi.fn().mockResolvedValue(body);

describe("getMyProfileSummary", () => {
  it("gösterim adını ve küçük avatarı alır", async () => {
    const result = await getMyProfileSummary(
      request({ displayName: " Ada ", username: "ada", avatar: { small: "/api/v1/user/media/k-s.jpg", large: "/api/v1/user/media/k.jpg" } })
    );
    expect(result.name).toBe("Ada");
    expect(result.avatarUrl).toMatch(/\/api\/v1\/user\/media\/k-s\.jpg$/);
  });

  it("gösterim adı yoksa kullanıcı adına düşer; ikisi de yoksa null", async () => {
    expect((await getMyProfileSummary(request({ displayName: null, username: "ada" }))).name).toBe("ada");
    expect((await getMyProfileSummary(request({ displayName: "", username: null }))).name).toBeNull();
  });

  it("avatar yoksa null; dış adres ve yol atlama reddedilir", async () => {
    expect((await getMyProfileSummary(request({ avatar: null }))).avatarUrl).toBeNull();
    expect((await getMyProfileSummary(request({ avatar: { small: "https://evil.example/x.jpg" } }))).avatarUrl).toBeNull();
    expect((await getMyProfileSummary(request({ avatar: { small: "/api/v1/user/media/../auth/me" } }))).avatarUrl).toBeNull();
  });

  it("beklenmeyen gövdede çökmez", async () => {
    expect(await getMyProfileSummary(request("x"))).toEqual({ name: null, avatarUrl: null });
    expect(await getMyProfileSummary(request(null))).toEqual({ name: null, avatarUrl: null });
  });

  it("isteği doğru yola atar", async () => {
    const fake = request({});
    await getMyProfileSummary(fake);
    expect(fake).toHaveBeenCalledWith("/api/v1/user/me", undefined);
  });
});
