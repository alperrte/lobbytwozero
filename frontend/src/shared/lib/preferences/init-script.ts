import {
  NAVBAR_STORAGE_KEY,
  SIDEBAR_STORAGE_KEY,
  THEME_STORAGE_KEY
} from "./preferences";

/**
 * <head> içinde ilk boyamadan önce çalışan küçük betik: temayı ve kenar çubuğu durumunu
 * <html> üzerine yazar. Navbar davranışı yalnız `fixed` ise yazılır (varsayılan: otomatik gizleme).
 * Depolama okunamazsa sistem temasına, genişletilmiş kenar çubuğuna ve otomatik gizlemeye düşer.
 */
export const preferencesInitScript = `(function(){var d=document.documentElement,t="system",s=null,n=null;try{var v=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY
)});if(v==="light"||v==="dark")t=v;s=localStorage.getItem(${JSON.stringify(
  SIDEBAR_STORAGE_KEY
)});n=localStorage.getItem(${JSON.stringify(NAVBAR_STORAGE_KEY)})}catch(e){}var r=t==="system"?(window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):t;d.dataset.theme=r;d.dataset.themePref=t;if(s==="collapsed")d.dataset.sidebar="collapsed";if(n==="fixed")d.dataset.navbar="fixed"})();`;
