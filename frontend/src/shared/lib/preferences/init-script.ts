import { SIDEBAR_STORAGE_KEY, THEME_STORAGE_KEY } from "./preferences";

/**
 * <head> içinde ilk boyamadan önce çalışan küçük betik: temayı ve kenar çubuğu durumunu
 * <html> üzerine yazar. Depolama okunamazsa sistem temasına ve genişletilmiş kenar çubuğuna düşer.
 */
export const preferencesInitScript = `(function(){var d=document.documentElement,t="system",s=null;try{var v=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY
)});if(v==="light"||v==="dark")t=v;s=localStorage.getItem(${JSON.stringify(
  SIDEBAR_STORAGE_KEY
)})}catch(e){}var r=t==="system"?(window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):t;d.dataset.theme=r;d.dataset.themePref=t;if(s==="collapsed")d.dataset.sidebar="collapsed"})();`;
