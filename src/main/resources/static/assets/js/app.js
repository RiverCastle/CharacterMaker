const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
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

let selectedFile = null;
let selectedBackground = null;
let overlayState = { xFrac: 0.35, yFrac: 0.35, wFrac: 0.3 };
let containerAspect = 1;
let caricatureAspect = 1;
let dragState = null;

loadBackgrounds();

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

compositeOverlay.addEventListener('pointerdown', (e) => {
	if (e.target === resizeHandle) {
		return;
	}
	e.preventDefault();
	dragState = {
		mode: 'move',
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
		pointerId: e.pointerId,
		startX: e.clientX,
		startY: e.clientY,
		startWFrac: overlayState.wFrac,
		rect: compositeStage.getBoundingClientRect()
	};
});

window.addEventListener('pointermove', (e) => {
	if (!dragState || e.pointerId !== dragState.pointerId) {
		return;
	}
	const dxFrac = (e.clientX - dragState.startX) / dragState.rect.width;
	const dyFrac = (e.clientY - dragState.startY) / dragState.rect.height;

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

function renderComposite() {
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

	const dataUrl = canvas.toDataURL('image/png');
	compositeResult.src = dataUrl;

	downloadBtn.href = dataUrl;
	downloadBtn.download = 'caricature-composite-' + Date.now() + '.png';
	downloadBtn.hidden = false;
}
