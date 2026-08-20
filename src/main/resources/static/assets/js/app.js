const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const caricatureDropZone = document.getElementById('caricatureDropZone');
const caricatureFileInput = document.getElementById('caricatureFileInput');
const originalPreview = document.getElementById('originalPreview');
const resultPreview = document.getElementById('resultPreview');
const convertBtn = document.getElementById('convertBtn');
const downloadBtn = document.getElementById('downloadBtn');
const loading = document.getElementById('loading');
const errorMessage = document.getElementById('errorMessage');

const backgroundGallery = document.getElementById('backgroundGallery');
const backgroundEmptyMessage = document.getElementById('backgroundEmptyMessage');
const compositeStage = document.getElementById('compositeStage');
const compositeBackground = document.getElementById('compositeBackground');
const compositeOverlay = document.getElementById('compositeOverlay');
const compositeOverlayImg = document.getElementById('compositeOverlayImg');
const resizeHandle = document.getElementById('resizeHandle');
const compositeBtn = document.getElementById('compositeBtn');
const compositeResult = document.getElementById('compositeResult');
const textOverlay = document.getElementById('textOverlay');
const textOverlayLabel = document.getElementById('textOverlayLabel');
const textResizeHandle = document.getElementById('textResizeHandle');
const promoTextInput = document.getElementById('promoTextInput');
const promoTextColor = document.getElementById('promoTextColor');
const promoFontSelect = document.getElementById('promoFontSelect');

let selectedFile = null;
let selectedBackground = null;
let overlayState = { xFrac: 0.35, yFrac: 0.35, wFrac: 0.3 };
let textOverlayState = { xFrac: 0.1, yFrac: 0.8, fontSizeFrac: 0.06, color: '#000000', fontId: null };
let containerAspect = 1;
let caricatureAspect = 1;
let dragState = null;
let fontRegistry = {};

loadBackgrounds();
loadFonts();

dropZone.addEventListener('click', () => fileInput.click());

dropZone.addEventListener('dragover', (e) => {
	e.preventDefault();
	dropZone.classList.add('dragover');
});

dropZone.addEventListener('dragleave', () => {
	dropZone.classList.remove('dragover');
});

dropZone.addEventListener('drop', (e) => {
	e.preventDefault();
	dropZone.classList.remove('dragover');
	if (e.dataTransfer.files.length > 0) {
		handleFile(e.dataTransfer.files[0]);
	}
});

fileInput.addEventListener('change', () => {
	if (fileInput.files.length > 0) {
		handleFile(fileInput.files[0]);
	}
});

convertBtn.addEventListener('click', convertToCaricature);

caricatureDropZone.addEventListener('click', () => caricatureFileInput.click());

caricatureDropZone.addEventListener('dragover', (e) => {
	e.preventDefault();
	caricatureDropZone.classList.add('dragover');
});

caricatureDropZone.addEventListener('dragleave', () => {
	caricatureDropZone.classList.remove('dragover');
});

caricatureDropZone.addEventListener('drop', (e) => {
	e.preventDefault();
	caricatureDropZone.classList.remove('dragover');
	if (e.dataTransfer.files.length > 0) {
		handlePrebuiltCaricature(e.dataTransfer.files[0]);
	}
});

caricatureFileInput.addEventListener('change', () => {
	if (caricatureFileInput.files.length > 0) {
		handlePrebuiltCaricature(caricatureFileInput.files[0]);
	}
});

function handlePrebuiltCaricature(file) {
	if (!file.type.startsWith('image/')) {
		showError('이미지 파일만 업로드할 수 있습니다.');
		return;
	}
	hideError();
	selectedFile = null;
	originalPreview.src = '';
	convertBtn.disabled = true;
	compositeResult.src = '';
	compositeStage.hidden = true;
	compositeBtn.disabled = true;
	textOverlay.hidden = true;

	const objectUrl = URL.createObjectURL(file);
	resultPreview.onload = () => updateCompositeStage();
	resultPreview.src = objectUrl;
	downloadBtn.href = objectUrl;
	downloadBtn.download = file.name || ('caricature-' + Date.now() + '.png');
	downloadBtn.hidden = false;
}

function handleFile(file) {
	if (!file.type.startsWith('image/')) {
		showError('이미지 파일만 업로드할 수 있습니다.');
		return;
	}
	hideError();
	selectedFile = file;
	originalPreview.src = URL.createObjectURL(file);
	resultPreview.src = '';
	convertBtn.disabled = false;
	downloadBtn.hidden = true;
	compositeResult.src = '';
	compositeStage.hidden = true;
	compositeBtn.disabled = true;
	textOverlay.hidden = true;
}

async function convertToCaricature() {
	if (!selectedFile) {
		return;
	}

	hideError();
	loading.hidden = false;
	convertBtn.disabled = true;
	downloadBtn.hidden = true;
	compositeResult.src = '';
	compositeStage.hidden = true;
	compositeBtn.disabled = true;
	textOverlay.hidden = true;

	const formData = new FormData();
	formData.append('photo', selectedFile);

	try {
		const response = await fetch('/api/caricature/convert', {
			method: 'POST',
			body: formData
		});

		if (!response.ok) {
			const message = await response.text();
			throw new Error(message || '변환에 실패했습니다.');
		}

		const data = await response.json();
		resultPreview.onload = () => updateCompositeStage();
		resultPreview.src = data.resultImageUrl + '?t=' + Date.now();
		downloadBtn.href = data.resultImageUrl;
		downloadBtn.download = 'caricature-' + data.id + '.png';
		downloadBtn.hidden = false;
	} catch (err) {
		showError(err.message);
	} finally {
		loading.hidden = true;
		convertBtn.disabled = false;
	}
}

function showError(message) {
	errorMessage.textContent = message;
	errorMessage.hidden = false;
}

function hideError() {
	errorMessage.hidden = true;
}

async function loadBackgrounds() {
	try {
		const response = await fetch('/api/admin/background');
		if (!response.ok) {
			return;
		}
		const images = await response.json();
		renderBackgroundGallery(images);
	} catch (err) {
		// 배경 이미지 목록을 불러오지 못해도 캐리커처 변환 자체는 가능하므로 조용히 무시한다.
	}
}

async function loadFonts() {
	try {
		const response = await fetch('/api/admin/font');
		if (!response.ok) {
			return;
		}
		const fonts = await response.json();
		renderFontOptions(fonts);
	} catch (err) {
		// 폰트 목록을 불러오지 못해도 기본 폰트로 계속 사용할 수 있으므로 조용히 무시한다.
	}
}

function renderFontOptions(fonts) {
	promoFontSelect.innerHTML = '<option value="">기본 폰트</option>';
	fontRegistry = {};

	for (const font of fonts) {
		fontRegistry[font.id] = { ...font, family: 'promo-font-' + font.id, loadPromise: null };

		const option = document.createElement('option');
		option.value = font.id;
		option.textContent = font.name;
		promoFontSelect.appendChild(option);
	}
}

function ensureFontLoaded(fontId) {
	const entry = fontRegistry[fontId];
	if (!entry) {
		return Promise.resolve(null);
	}
	if (!entry.loadPromise) {
		const fontFace = new FontFace(entry.family, `url(${entry.fontUrl}) format('${entry.format}')`);
		entry.loadPromise = fontFace.load().then((loaded) => {
			document.fonts.add(loaded);
			return entry.family;
		});
	}
	return entry.loadPromise;
}

promoFontSelect.addEventListener('change', () => {
	textOverlayState.fontId = promoFontSelect.value || null;
	applyTextOverlayStyle();
});

function renderBackgroundGallery(images) {
	backgroundGallery.innerHTML = '';
	backgroundEmptyMessage.hidden = images.length > 0;

	for (const image of images) {
		const item = document.createElement('div');
		item.className = 'bg-thumb';

		const img = document.createElement('img');
		img.src = image.imageUrl;
		img.alt = '배경 이미지';
		item.appendChild(img);

		const check = document.createElement('span');
		check.className = 'bg-thumb-check';
		check.textContent = '✓';
		item.appendChild(check);

		item.addEventListener('click', () => selectBackground(image, item));
		backgroundGallery.appendChild(item);
	}
}

function selectBackground(image, itemEl) {
	selectedBackground = image;
	for (const el of backgroundGallery.children) {
		el.classList.toggle('selected', el === itemEl);
	}
	updateCompositeStage();
}

function updateCompositeStage() {
	if (!selectedBackground || !resultPreview.src || !resultPreview.naturalWidth) {
		compositeStage.hidden = true;
		compositeBtn.disabled = true;
		return;
	}

	compositeBackground.onload = () => {
		containerAspect = compositeBackground.naturalWidth / compositeBackground.naturalHeight;
		compositeStage.style.aspectRatio = String(containerAspect);
		caricatureAspect = resultPreview.naturalWidth / resultPreview.naturalHeight;
		overlayState = { xFrac: 0.35, yFrac: 0.35, wFrac: 0.3 };
		compositeOverlayImg.src = resultPreview.src;
		applyOverlayStyle();
		compositeStage.hidden = false;
		compositeBtn.disabled = false;
		updateTextOverlayVisibility();
	};
	compositeBackground.src = selectedBackground.imageUrl;
}

function applyOverlayStyle() {
	const hFrac = overlayState.wFrac * containerAspect / caricatureAspect;
	compositeOverlay.style.left = (overlayState.xFrac * 100) + '%';
	compositeOverlay.style.top = (overlayState.yFrac * 100) + '%';
	compositeOverlay.style.width = (overlayState.wFrac * 100) + '%';
	compositeOverlay.style.height = (hFrac * 100) + '%';
}

function applyTextOverlayStyle() {
	textOverlay.style.left = (textOverlayState.xFrac * 100) + '%';
	textOverlay.style.top = (textOverlayState.yFrac * 100) + '%';
	textOverlayLabel.style.fontSize = (textOverlayState.fontSizeFrac * 100) + 'cqw';
	textOverlayLabel.style.color = textOverlayState.color;
	textOverlayLabel.textContent = promoTextInput.value;

	if (textOverlayState.fontId && fontRegistry[textOverlayState.fontId]) {
		const family = fontRegistry[textOverlayState.fontId].family;
		textOverlayLabel.style.fontFamily = `'${family}', sans-serif`;
		ensureFontLoaded(textOverlayState.fontId);
	} else {
		textOverlayLabel.style.fontFamily = '';
	}
}

function updateTextOverlayVisibility() {
	const hasText = promoTextInput.value.trim().length > 0;
	textOverlay.hidden = !hasText || compositeStage.hidden;
	if (!textOverlay.hidden) {
		applyTextOverlayStyle();
	}
}

promoTextInput.addEventListener('input', updateTextOverlayVisibility);

promoTextColor.addEventListener('input', () => {
	textOverlayState.color = promoTextColor.value;
	applyTextOverlayStyle();
});

compositeOverlay.addEventListener('pointerdown', (e) => {
	if (e.target === resizeHandle) {
		return;
	}
	e.preventDefault();
	dragState = {
		mode: 'move',
		target: 'image',
		pointerId: e.pointerId,
		startX: e.clientX,
		startY: e.clientY,
		startXFrac: overlayState.xFrac,
		startYFrac: overlayState.yFrac,
		rect: compositeStage.getBoundingClientRect()
	};
});

resizeHandle.addEventListener('pointerdown', (e) => {
	e.preventDefault();
	e.stopPropagation();
	dragState = {
		mode: 'resize',
		target: 'image',
		pointerId: e.pointerId,
		startX: e.clientX,
		startY: e.clientY,
		startWFrac: overlayState.wFrac,
		rect: compositeStage.getBoundingClientRect()
	};
});

textOverlay.addEventListener('pointerdown', (e) => {
	if (e.target === textResizeHandle) {
		return;
	}
	e.preventDefault();
	dragState = {
		mode: 'move',
		target: 'text',
		pointerId: e.pointerId,
		startX: e.clientX,
		startY: e.clientY,
		startXFrac: textOverlayState.xFrac,
		startYFrac: textOverlayState.yFrac,
		rect: compositeStage.getBoundingClientRect()
	};
});

textResizeHandle.addEventListener('pointerdown', (e) => {
	e.preventDefault();
	e.stopPropagation();
	dragState = {
		mode: 'resize',
		target: 'text',
		pointerId: e.pointerId,
		startX: e.clientX,
		startY: e.clientY,
		startFontSizeFrac: textOverlayState.fontSizeFrac,
		rect: compositeStage.getBoundingClientRect()
	};
});

window.addEventListener('pointermove', (e) => {
	if (!dragState || e.pointerId !== dragState.pointerId) {
		return;
	}
	const dxFrac = (e.clientX - dragState.startX) / dragState.rect.width;
	const dyFrac = (e.clientY - dragState.startY) / dragState.rect.height;

	if (dragState.target === 'image') {
		if (dragState.mode === 'move') {
			const hFrac = overlayState.wFrac * containerAspect / caricatureAspect;
			const xFrac = clamp(dragState.startXFrac + dxFrac, 0, 1 - overlayState.wFrac);
			const yFrac = clamp(dragState.startYFrac + dyFrac, 0, 1 - hFrac);
			overlayState.xFrac = xFrac;
			overlayState.yFrac = yFrac;
		} else if (dragState.mode === 'resize') {
			const minWFrac = 0.06;
			const maxWFracByRight = 1 - overlayState.xFrac;
			const maxWFracByBottom = (1 - overlayState.yFrac) * caricatureAspect / containerAspect;
			const maxWFrac = Math.min(maxWFracByRight, maxWFracByBottom);
			overlayState.wFrac = clamp(dragState.startWFrac + dxFrac, minWFrac, maxWFrac);
		}
		applyOverlayStyle();
	} else if (dragState.target === 'text') {
		if (dragState.mode === 'move') {
			textOverlayState.xFrac = clamp(dragState.startXFrac + dxFrac, 0, 0.98);
			textOverlayState.yFrac = clamp(dragState.startYFrac + dyFrac, 0, 0.98);
		} else if (dragState.mode === 'resize') {
			const minFontSizeFrac = 0.02;
			const maxFontSizeFrac = 0.25;
			textOverlayState.fontSizeFrac = clamp(dragState.startFontSizeFrac + dyFrac, minFontSizeFrac, maxFontSizeFrac);
		}
		applyTextOverlayStyle();
	}
});

window.addEventListener('pointerup', (e) => {
	if (dragState && e.pointerId === dragState.pointerId) {
		dragState = null;
	}
});

window.addEventListener('pointercancel', () => {
	dragState = null;
});

function clamp(value, min, max) {
	return Math.min(Math.max(value, min), max);
}

compositeBtn.addEventListener('click', renderComposite);

async function renderComposite() {
	if (!selectedBackground || !compositeBackground.naturalWidth) {
		return;
	}

	const canvas = document.createElement('canvas');
	canvas.width = compositeBackground.naturalWidth;
	canvas.height = compositeBackground.naturalHeight;
	const ctx = canvas.getContext('2d');
	ctx.drawImage(compositeBackground, 0, 0, canvas.width, canvas.height);

	const w = overlayState.wFrac * canvas.width;
	const h = w / caricatureAspect;
	const x = overlayState.xFrac * canvas.width;
	const y = overlayState.yFrac * canvas.height;
	ctx.drawImage(resultPreview, x, y, w, h);

	const promoText = promoTextInput.value.trim();
	if (promoText) {
		const fontFamily = textOverlayState.fontId ? await ensureFontLoaded(textOverlayState.fontId) : null;
		const fontSizePx = textOverlayState.fontSizeFrac * canvas.width;
		const lineHeightPx = fontSizePx * 1.25;
		const textX = textOverlayState.xFrac * canvas.width;
		const textY = textOverlayState.yFrac * canvas.height;

		const fontFamilyCss = fontFamily ? `'${fontFamily}', sans-serif` : 'sans-serif';
		ctx.font = `bold ${fontSizePx}px ${fontFamilyCss}`;
		ctx.textBaseline = 'top';
		ctx.fillStyle = textOverlayState.color;
		ctx.lineWidth = Math.max(2, fontSizePx * 0.08);
		ctx.strokeStyle = 'rgba(0, 0, 0, 0.65)';
		ctx.lineJoin = 'round';

		promoText.split('\n').forEach((line, i) => {
			const lineY = textY + i * lineHeightPx;
			ctx.strokeText(line, textX, lineY);
			ctx.fillText(line, textX, lineY);
		});
	}

	const dataUrl = canvas.toDataURL('image/png');
	compositeResult.src = dataUrl;

	downloadBtn.href = dataUrl;
	downloadBtn.download = 'caricature-composite-' + Date.now() + '.png';
	downloadBtn.hidden = false;
}
