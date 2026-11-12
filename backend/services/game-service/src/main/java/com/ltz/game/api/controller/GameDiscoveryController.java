package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.GameDiscoveryRequest;
import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.application.port.AdultAccess;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.application.service.GameDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/games") @RequiredArgsConstructor
public class GameDiscoveryController {
    private final GameDiscoveryService service;
    @GetMapping
    public ResponseEntity<GameDiscoveryResponse> list(@ModelAttribute GameDiscoveryRequest request,AdultAccess access) { return respond(request,access); }
    @GetMapping("/search")
    public ResponseEntity<GameDiscoveryResponse> search(@RequestParam String q,@ModelAttribute GameDiscoveryRequest request,AdultAccess access) {
        request.setQ(q);
        return respond(request,access);
    }
    /**
     * {@code includeAdult} yalnız yaşı doğrulanmış kullanıcıda (token'daki {@code adult_verified=true}) uygulanır; aksi halde sessizce yok sayılır ve yanıtta
     * {@code includeAdultApplied=false} döner. Kullanıcıya göre değişebilen bu yanıt aracı önbellekte paylaşılmasın diye yalnız {@code includeAdult} istenen
     * yanıtlarda {@code private, no-store} ve {@code Vary} gönderilir; diğer yanıtların davranışı değişmez.
     */
    private ResponseEntity<GameDiscoveryResponse> respond(GameDiscoveryRequest request,AdultAccess access) {
        boolean requested=request.isIncludeAdult();
        GameDiscoveryResponse body=service.list(request.toQuery(access.allows(requested)));
        ResponseEntity.BodyBuilder ok=ResponseEntity.ok();
        if(requested) ok.cacheControl(CacheControl.noStore().cachePrivate()).varyBy("Authorization","Cookie");
        return ok.body(body);
    }
    @GetMapping("/popular")
    public GameDiscoveryResponse popular(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(DiscoveryQuery.popular(page,size));
    }
}
