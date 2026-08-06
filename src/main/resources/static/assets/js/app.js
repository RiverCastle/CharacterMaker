const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const originalPreview = document.getElementById('originalPreview');
const resultPreview = document.getElementById('resultPreview');
const convertBtn = document.getElementById('convertBtn');
const downloadBtn = document.getElementById('downloadBtn');
const loading = document.getElementById('loading');
const errorMessage = document.getElementById('errorMessage');

let selectedFile = null;

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
}

async function convertToCaricature() {
	if (!selectedFile) {
		return;
	}

	hideError();
	loading.hidden = false;
	convertBtn.disabled = true;
	downloadBtn.hidden = true;

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
