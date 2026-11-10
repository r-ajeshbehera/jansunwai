import { createContext, useContext, useEffect, useState } from 'react';

/**
 * UI text in English, Hindi and Odia. Complaint text itself is stored as the citizen wrote it;
 * the AI adds an English summary/translation for officers. Missing keys fall back to English.
 */
const dict = {
  en: {
    appTagline: 'Civic grievance portal', home: 'Home', track: 'Track complaint', login: 'Sign in', register: 'Create account',
    logout: 'Log out', myComplaints: 'My complaints', newComplaint: 'New complaint', notifications: 'Notifications',
    profile: 'Profile', dashboard: 'Dashboard', complaints: 'Complaints', review: 'Review', duplicates: 'Duplicates',
    escalations: 'Overdue', myWork: 'My assignments', users: 'Users', sla: 'SLA rules', departments: 'Departments',
    audit: 'Audit log', aiUsage: 'AI usage', wardReports: 'Ward reports', map: 'City map',
    heroTitle: 'Report a civic problem in your language',
    heroText: 'Potholes, water supply, drainage, garbage or streetlights: write in English, हिन्दी or ଓଡ଼ିଆ. We route it to the right department and you can track every step.',
    reportNow: 'Report a problem', trackNow: 'Track with your ID',
    step1: 'Write in your language', step1t: 'Describe the problem and pin the place on the map. Add a photo if you can.',
    step2: 'AI helps, officers decide', step2t: 'AI suggests the category and department and spots duplicates. An officer always confirms.',
    step3: 'Deadline and tracking', step3t: 'Every accepted complaint gets a resolution deadline. Overdue ones are escalated automatically.',
    email: 'Email', password: 'Password', fullName: 'Full name', phone: 'Mobile number', noAccount: 'No account yet?',
    haveAccount: 'Already registered?', demoAccounts: 'Demo accounts (password Demo@1234)',
    title: 'Title', description: 'Describe the problem', language: 'Language', landmark: 'Landmark (optional)',
    location: 'Location', useMyLocation: 'Use my location', clickMap: 'Click the map to set the location', submit: 'Submit',
    optionalPhoto: 'Photo (optional, JPEG/PNG/WebP, max 5 MB)', submitted: 'Complaint submitted', yourTrackingId: 'Your tracking ID',
    status: 'Status', category: 'Category', priority: 'Priority', department: 'Department', ward: 'Ward', created: 'Submitted',
    dueBy: 'Resolve by', overdue: 'Overdue', timeline: 'Timeline', photos: 'Photos', remarks: 'Remarks', addRemark: 'Add remark',
    trackingId: 'Tracking ID', search: 'Search', all: 'All', noData: 'Nothing here yet.', loading: 'Loading…',
    confirmClose: 'Problem solved, close it', reopen: 'Not solved, reopen', reason: 'Reason', save: 'Save', cancel: 'Cancel',
    approve: 'Approve', reject: 'Reject', reroute: 'Send to another department', assign: 'Assign', reassign: 'Reassign',
    startWork: 'Start work', submitResolution: 'Submit resolution', approveResolution: 'Approve resolution', sendBack: 'Send back',
    uploadPhoto: 'Upload photo', aiSuggestion: 'AI suggestion', confidence: 'confidence', mockNotice: 'Offline mock AI (demo)',
    markRead: 'Mark read', unread: 'unread', trackHint: 'Enter the tracking ID you received, e.g. JS-2026-001001',
    expectedBy: 'Expected by', publicNote: 'No personal details are shown on this page.',
    demoBanner: 'Demonstration system with sample data. Not an official government service.',
  },
  hi: {
    appTagline: 'नागरिक शिकायत पोर्टल', home: 'होम', track: 'शिकायत ट्रैक करें', login: 'साइन इन', register: 'खाता बनाएँ',
    logout: 'लॉग आउट', myComplaints: 'मेरी शिकायतें', newComplaint: 'नई शिकायत', notifications: 'सूचनाएँ', profile: 'प्रोफ़ाइल',
    dashboard: 'डैशबोर्ड', complaints: 'शिकायतें', review: 'समीक्षा सूची', duplicates: 'दोहराई गई शिकायतें',
    escalations: 'समय-सीमा पार', myWork: 'मेरे कार्य', map: 'शहर का नक्शा',
    heroTitle: 'अपनी भाषा में नागरिक समस्या दर्ज करें',
    heroText: 'गड्ढे, पानी, नाली, कचरा या स्ट्रीटलाइट: English, हिन्दी या ଓଡ଼ିଆ में लिखें। हम इसे सही विभाग तक पहुँचाते हैं और आप हर कदम देख सकते हैं।',
    reportNow: 'समस्या दर्ज करें', trackNow: 'आईडी से ट्रैक करें',
    step1: 'अपनी भाषा में लिखें', step1t: 'समस्या बताएँ और नक्शे पर जगह चुनें। हो सके तो फ़ोटो जोड़ें।',
    step2: 'AI मदद करता है, अधिकारी तय करते हैं', step2t: 'AI श्रेणी और विभाग सुझाता है और दोहराई शिकायतें पहचानता है। अंतिम निर्णय अधिकारी का होता है।',
    step3: 'समय-सीमा और ट्रैकिंग', step3t: 'हर स्वीकृत शिकायत की समाधान समय-सीमा तय होती है। देरी होने पर अपने-आप ऊपर भेजी जाती है।',
    email: 'ईमेल', password: 'पासवर्ड', fullName: 'पूरा नाम', phone: 'मोबाइल नंबर', noAccount: 'खाता नहीं है?',
    haveAccount: 'पहले से पंजीकृत?', title: 'शीर्षक', description: 'समस्या का विवरण', language: 'भाषा', landmark: 'पास की पहचान (वैकल्पिक)',
    location: 'स्थान', useMyLocation: 'मेरा स्थान लें', clickMap: 'स्थान चुनने के लिए नक्शे पर क्लिक करें', submit: 'जमा करें',
    optionalPhoto: 'फ़ोटो (वैकल्पिक, अधिकतम 5 MB)', submitted: 'शिकायत दर्ज हो गई', yourTrackingId: 'आपकी ट्रैकिंग आईडी',
    status: 'स्थिति', category: 'श्रेणी', priority: 'प्राथमिकता', department: 'विभाग', ward: 'वार्ड', created: 'दर्ज की गई',
    dueBy: 'समाधान की तिथि', overdue: 'समय-सीमा पार', timeline: 'प्रगति', photos: 'फ़ोटो', remarks: 'टिप्पणियाँ',
    addRemark: 'टिप्पणी जोड़ें', trackingId: 'ट्रैकिंग आईडी', search: 'खोजें', all: 'सभी', noData: 'अभी कुछ नहीं है।',
    loading: 'लोड हो रहा है…', confirmClose: 'समस्या हल हो गई, बंद करें', reopen: 'हल नहीं हुई, फिर खोलें', reason: 'कारण',
    save: 'सहेजें', cancel: 'रद्द करें', markRead: 'पढ़ा गया', unread: 'अपठित',
    trackHint: 'मिली हुई ट्रैकिंग आईडी डालें, जैसे JS-2026-001001', expectedBy: 'अपेक्षित तिथि',
    publicNote: 'इस पेज पर कोई व्यक्तिगत जानकारी नहीं दिखाई जाती।',
    demoBanner: 'नमूना डेटा वाला प्रदर्शन सिस्टम। यह आधिकारिक सरकारी सेवा नहीं है।',
  },
  or: {
    appTagline: 'ନାଗରିକ ଅଭିଯୋଗ ପୋର୍ଟାଲ', home: 'ମୂଳ ପୃଷ୍ଠା', track: 'ଅଭିଯୋଗ ଟ୍ରାକ କରନ୍ତୁ', login: 'ସାଇନ ଇନ', register: 'ଖାତା ଖୋଲନ୍ତୁ',
    logout: 'ଲଗ ଆଉଟ', myComplaints: 'ମୋ ଅଭିଯୋଗ', newComplaint: 'ନୂଆ ଅଭିଯୋଗ', notifications: 'ସୂଚନା', profile: 'ପ୍ରୋଫାଇଲ',
    dashboard: 'ଡ୍ୟାସବୋର୍ଡ', complaints: 'ଅଭିଯୋଗ', map: 'ସହର ମାନଚିତ୍ର',
    heroTitle: 'ନିଜ ଭାଷାରେ ନାଗରିକ ସମସ୍ୟା ଜଣାନ୍ତୁ',
    heroText: 'ଗାତ, ପାଣି, ନାଳ, ଅଳିଆ କିମ୍ବା ରାସ୍ତା ଆଲୋକ: English, हिन्दी କିମ୍ବା ଓଡ଼ିଆରେ ଲେଖନ୍ତୁ। ଆମେ ଏହାକୁ ଠିକ ବିଭାଗକୁ ପଠାଉ ଏବଂ ଆପଣ ପ୍ରତି ପଦକ୍ଷେପ ଦେଖିପାରିବେ।',
    reportNow: 'ସମସ୍ୟା ଜଣାନ୍ତୁ', trackNow: 'ID ଦ୍ୱାରା ଟ୍ରାକ କରନ୍ତୁ',
    step1: 'ନିଜ ଭାଷାରେ ଲେଖନ୍ତୁ', step1t: 'ସମସ୍ୟା ବର୍ଣ୍ଣନା କରନ୍ତୁ ଏବଂ ମାନଚିତ୍ରରେ ସ୍ଥାନ ବାଛନ୍ତୁ।',
    step2: 'AI ସାହାଯ୍ୟ କରେ, ଅଧିକାରୀ ନିଷ୍ପତ୍ତି ନିଅନ୍ତି', step2t: 'AI ବର୍ଗ ଓ ବିଭାଗ ପରାମର୍ଶ ଦିଏ। ଶେଷ ନିଷ୍ପତ୍ତି ଅଧିକାରୀଙ୍କର।',
    step3: 'ସମୟସୀମା ଓ ଟ୍ରାକିଂ', step3t: 'ପ୍ରତ୍ୟେକ ଗ୍ରହଣ କରାଯାଇଥିବା ଅଭିଯୋଗର ସମାଧାନ ସମୟସୀମା ରହେ।',
    email: 'ଇମେଲ', password: 'ପାସୱାର୍ଡ', fullName: 'ପୂରା ନାମ', phone: 'ମୋବାଇଲ ନମ୍ବର', title: 'ଶୀର୍ଷକ',
    description: 'ସମସ୍ୟାର ବିବରଣୀ', language: 'ଭାଷା', location: 'ସ୍ଥାନ', useMyLocation: 'ମୋ ସ୍ଥାନ ନିଅନ୍ତୁ', submit: 'ଦାଖଲ କରନ୍ତୁ',
    submitted: 'ଅଭିଯୋଗ ଦାଖଲ ହେଲା', yourTrackingId: 'ଆପଣଙ୍କ ଟ୍ରାକିଂ ID', status: 'ସ୍ଥିତି', category: 'ବର୍ଗ', priority: 'ପ୍ରାଥମିକତା',
    department: 'ବିଭାଗ', ward: 'ୱାର୍ଡ', timeline: 'ଅଗ୍ରଗତି', trackingId: 'ଟ୍ରାକିଂ ID', search: 'ଖୋଜନ୍ତୁ', loading: 'ଲୋଡ ହେଉଛି…',
    noData: 'ଏପର୍ଯ୍ୟନ୍ତ କିଛି ନାହିଁ।', confirmClose: 'ସମାଧାନ ହେଲା, ବନ୍ଦ କରନ୍ତୁ', reopen: 'ସମାଧାନ ହୋଇନି, ପୁଣି ଖୋଲନ୍ତୁ',
    trackHint: 'ମିଳିଥିବା ଟ୍ରାକିଂ ID ଦିଅନ୍ତୁ, ଯେପରି JS-2026-001001', publicNote: 'ଏହି ପୃଷ୍ଠାରେ କୌଣସି ବ୍ୟକ୍ତିଗତ ତଥ୍ୟ ଦେଖାଯାଏ ନାହିଁ।',
    demoBanner: 'ନମୁନା ତଥ୍ୟ ସହିତ ପ୍ରଦର୍ଶନ ସିଷ୍ଟମ। ଏହା ସରକାରୀ ସେବା ନୁହେଁ।',
  },
};

export const LANGS = [{ code: 'en', label: 'English' }, { code: 'hi', label: 'हिन्दी' }, { code: 'or', label: 'ଓଡ଼ିଆ' }];

const I18nContext = createContext(null);

export function I18nProvider({ children }) {
  const [lang, setLang] = useState(() => {
    try { return localStorage.getItem('jansunwai.lang') || 'en'; } catch { return 'en'; }
  });
  useEffect(() => {
    document.documentElement.lang = lang;
    try { localStorage.setItem('jansunwai.lang', lang); } catch { /* private mode */ }
  }, [lang]);
  const t = (key) => dict[lang]?.[key] ?? dict.en[key] ?? key;
  return <I18nContext.Provider value={{ lang, setLang, t }}>{children}</I18nContext.Provider>;
}

export const useI18n = () => useContext(I18nContext);
