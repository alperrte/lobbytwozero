import {
  NAVBAR_STORAGE_KEY,
  SIDEBAR_STORAGE_KEY,
  THEME_STORAGE_KEY,
  TRANSITION_SOUND_STORAGE_KEY,
  TRANSITION_STORAGE_KEY
} from "./preferences";

/**
 * <head> içinde ilk boyamadan önce çalışan küçük betik: temayı ve kenar çubuğu durumunu
 * <html> üzerine yazar. Navbar davranışı yalnız `fixed`, tema geçişi paketi yalnız `v2` / `v1` / `minimal`,
 * geçiş sesi yalnız `off` ise yazılır (varsayılanlar nitelik taşımaz: otomatik gizleme, v3 taktik operasyon, ses açık).
 * Depolama okunamazsa sistem temasına, genişletilmiş kenar çubuğuna, otomatik gizlemeye, v3'e ve açık sese düşer.
 */
export const preferencesInitScript = `(function(){var d=document.documentElement,t="system",s=null,n=null,x=null,a=null;try{var v=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY
)});if(v==="light"||v==="dark")t=v;s=localStorage.getItem(${JSON.stringify(
  SIDEBAR_STORAGE_KEY
)});n=localStorage.getItem(${JSON.stringify(NAVBAR_STORAGE_KEY)});x=localStorage.getItem(${JSON.stringify(
  TRANSITION_STORAGE_KEY
)});a=localStorage.getItem(${JSON.stringify(
  TRANSITION_SOUND_STORAGE_KEY
)})}catch(e){}var r=t==="system"?(window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):t;d.dataset.theme=r;d.dataset.themePref=t;if(s==="collapsed")d.dataset.sidebar="collapsed";if(n==="fixed")d.dataset.navbar="fixed";if(x==="v2"||x==="v1"||x==="minimal")d.dataset.transition=x;if(a==="off")d.dataset.transitionSound="off"})();`;
