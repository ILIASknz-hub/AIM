/*
 * Aïn Souk v2 — génération des fichiers de configuration (à lancer avec Node.js 18+)
 *
 *   node outils/generer_config.mjs            -> régénère les deux fichiers avec les comptes ci-dessous
 *   node outils/generer_config.mjs --cles     -> génère aussi de NOUVELLES clés de chiffrement
 *
 * Fichiers produits :
 *   app/src/main/assets/www/config.js   (téléphones : comptes des auxiliaires, souks, clés PUBLIQUES)
 *   poste_annexe/config_annexe.js       (poste de l'annexe : tous les comptes, souks, clés PRIVÉES)
 *
 * Pour créer un compte : ajouter une ligne dans COMPTES, puis relancer ce script.
 * (Les comptes peuvent aussi être créés directement dans le poste de l'annexe, menu « Comptes et accès ».)
 */
import {webcrypto as crypto} from 'crypto';import fs from 'fs';import path from 'path';import {fileURLToPath} from 'url';
const ROOT=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const ITER=100000;

/* ---------- Comptes (le mot de passe est transformé en empreinte, il n'est jamais stocké en clair) ---------- */
const COMPTES=[
 // identifiant, mot de passe initial, rôle (admin | agent | auxiliaire), nom, fonction, souks affectés (codes)
 ['admin',  'Annexe@2026','admin',     'Administrateur du poste','',      []],
 ['agent01','Caid@2026',  'agent',     'Agent d\'autorité',     'قائد',   []],
 ['aux01',  'Souk@2026',  'auxiliaire','أحمد العلوي',           'مقدم',   ['sk','mbk']],
 ['aux02',  'Souk@2026',  'auxiliaire','محمد بناني',            'شيخ',    ['hk','jm','ad']],
 ['aux03',  'Souk@2026',  'auxiliaire','يوسف الإدريسي',         'مقدم',   ['dg','nr','kh']],
];

/* ---------- Référentiel des souks hebdomadaires (codes stables) ---------- */
const SOUKS=[
 ['sk','Sidi Kacem','سيدي قاسم','Municipalité',34.217,-5.700],
 ['mbk','Mechra Bel Ksiri','مشرع بلقصيري','Municipalité',34.560,-5.950],
 ['hk','Had Kourt','حد كورت','Municipalité',34.617,-5.733],
 ['jm','Jorf El Melha','جرف الملحة','Municipalité',34.483,-5.517],
 ['dg','Dar Gueddari','دار الكداري','Municipalité',34.417,-6.083],
 ['ad','Aïn Dfali','عين الدفالي','Commune rurale',34.599,-5.549],
 ['sel','Selfat','سلفات','Commune rurale',34.224,-5.476],
 ['kh','Khnichet','الخنيشات','Commune rurale',34.438,-5.684],
 ['zg','Zaggota','زكوطة','Commune rurale',34.167,-5.533],
 ['nr','Nouirate','النويرات','Commune rurale',34.568,-5.893],
 ['bt','Bab Tiouka','باب تيوكة','Commune rurale',null,null],
 ['btl','Bir Taleb','بئر الطالب','Commune rurale',null,null],
 ['chb','Chbanate','الشبانات','Commune rurale',null,null],
 ['dla','Dar Laaslouji','دار العسلوجي','Commune rurale',null,null],
 ['hw','Al Haouafate','الحوافات','Commune rurale',null,null],
 ['lmr','Lamrabih','المرابيح','Commune rurale',null,null],
 ['mak','Moulay Abdelkader','مولاي عبد القادر','Commune rurale',null,null],
 ['onl','Oulad Nouel','أولاد نوال','Commune rurale',null,null],
 ['rml','Ermilate','الرميلات','Commune rurale',null,null],
 ['sfs','Sefsaf','الصفصاف','Commune rurale',null,null],
 ['sab','Sidi Ahmed Benaissa','سيدي أحمد بنعيسى','Commune rurale',null,null],
 ['skm','Sidi Al Kamel','سيدي الكامل','Commune rurale',null,null],
 ['sah','Sidi Ameur Al Hadi','سيدي اعمر الحاضي','Commune rurale',null,null],
 ['saz','Sidi Azzouz','سيدي عزوز','Commune rurale',null,null],
 ['smc','Sidi M\'Hamed Chelh','سيدي امحمد الشلح','Commune rurale',null,null],
 ['tgh','Taoughilt','تاوغيلت','Commune rurale',null,null],
 ['tkn','Tekna','تكنة','Commune rurale',null,null],
 ['zrr','Zirara','زيرارة','Commune rurale',null,null],
 ['bnw','Bni Oual','بني وال','Commune rurale',null,null],
].map(([id,fr,ar,statut,lat,lng])=>({id,fr,ar,commune:fr,statut,jour:id==='hk'?'dim':'',lat,lng,actif:true}));

const b64=u=>Buffer.from(u).toString('base64');
async function empreinte(mdp,sel){const k=await crypto.subtle.importKey('raw',new TextEncoder().encode(mdp),'PBKDF2',false,['deriveBits']);
 return b64(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:Buffer.from(sel,'base64'),iterations:ITER},k,256))}
async function nouvellesCles(){const enc=await crypto.subtle.generateKey({name:'RSA-OAEP',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['encrypt','decrypt']);
 const sig=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);const j=k=>crypto.subtle.exportKey('jwk',k);
 return {kid:'K'+Date.now().toString(36).toUpperCase(),encPub:await j(enc.publicKey),encPriv:await j(enc.privateKey),sigPub:await j(sig.publicKey),sigPriv:await j(sig.privateKey)}}

const fCles=path.join(ROOT,'outils','.cles_annexe.json');
let K;
if(process.argv.includes('--cles')||!fs.existsSync(fCles)){
 const prev=path.join(ROOT,'poste_annexe','config_annexe.js');
 if(!process.argv.includes('--cles')&&fs.existsSync(prev)){const t=fs.readFileSync(prev,'utf8');const o=JSON.parse(t.slice(t.indexOf('{'),t.lastIndexOf('}')+1));K=o.cles}
 else K=await nouvellesCles();
} else K=JSON.parse(fs.readFileSync(fCles,'utf8'));

const comptes=[];
for(const [id,mdp,role,nom,fonction,souks] of COMPTES){const sel=b64(crypto.getRandomValues(new Uint8Array(16)));comptes.push({id,role,nom,fonction,souks,sel,hash:await empreinte(mdp,sel),iter:ITER,actif:true,cree:new Date().toISOString().slice(0,10)})}
const version=Date.now();
const tel={version,genere:new Date().toISOString(),kid:K.kid,cleChiffrement:K.encPub,cleSignature:K.sigPub,comptes:comptes.filter(c=>c.role==='auxiliaire'),souks:SOUKS.map(({id,fr,ar,jour,actif})=>({id,fr,ar,jour,actif}))};
const annexe={version,genere:tel.genere,cles:K,comptes,souks:SOUKS};
fs.writeFileSync(path.join(ROOT,'app/src/main/assets/www/config.js'),'/* Aïn Souk v2 — configuration des téléphones (générée automatiquement : ne pas modifier à la main).\n   Contient les comptes des auxiliaires (empreintes des mots de passe), les souks et les clés PUBLIQUES. */\nwindow.AINSOUK_CONFIG = '+JSON.stringify(tel,null,1)+';\n');
fs.writeFileSync(path.join(ROOT,'poste_annexe/config_annexe.js'),'/* Aïn Souk v2 — configuration du poste de l\'annexe (CONFIDENTIEL : contient les clés privées).\n   Ce fichier doit rester sur l\'ordinateur de l\'annexe, à côté de poste_annexe.html. */\nwindow.AINSOUK_ANNEXE = '+JSON.stringify(annexe,null,1)+';\n');
console.log('Configuration générée — clés',K.kid,'—',comptes.length,'comptes —',SOUKS.length,'souks');
