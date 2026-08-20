const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const gallery = document.getElementById('gallery');
const emptyMessage = document.getElementById('emptyMessage');
const statusMessage = document.getElementById('statusMessage');

loadGallery();

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
		uploadFile(e.dataTransfer.files[0]);
	}
});

fileInput.addEventListener('change', () => {
	if (fileInput.files.length > 0) {
		uploadFile(fileInput.files[0]);
	}
	fileInput.value = '';
});

async function uploadFile(file) {
	if (!file.type.startsWith('image/')) {
		showStatus('이미지 파일만 업로드할 수 있습니다.');
		return;
	}

	hideStatus();

	const formData = new FormData();
	formData.append('backgroundImage', file);

	try {
		const response = await fetch('/api/admin/background', {
			method: 'POST',
			body: formData
		});

		if (!response.ok) {
			const message = await response.text();
			throw new Error(message || '배경 이미지 저장에 실패했습니다.');
		}

		showStatus('배경 이미지가 추가되었습니다.', false);
		await loadGallery();
	} catch (err) {
		showStatus(err.message);
	}
}

async function loadGallery() {
	try {
		const response = await fetch('/api/admin/background');
		if (!response.ok) {
			throw new Error('배경 이미지 목록을 불러오지 못했습니다.');
		}
		const images = await response.json();
		renderGallery(images);
	} catch (err) {
		showStatus(err.message);
	}
}

function renderGallery(images) {
	gallery.innerHTML = '';
	emptyMessage.hidden = images.length > 0;

	for (const image of images) {
		const item = document.createElement('div');
		item.className = 'background-item';

		const img = document.createElement('img');
		img.src = image.imageUrl + '?t=' + Date.now();
		img.alt = '배경 이미지';
		item.appendChild(img);

		const deleteBtn = document.createElement('button');
		deleteBtn.type = 'button';
		deleteBtn.className = 'delete-btn';
		deleteBtn.textContent = '×';
		deleteBtn.title = '삭제';
		deleteBtn.addEventListener('click', () => deleteImage(image.id));
		item.appendChild(deleteBtn);

		gallery.appendChild(item);
	}
}

async function deleteImage(id) {
	hideStatus();
	try {
		const response = await fetch('/api/admin/background/' + id, { method: 'DELETE' });
		if (!response.ok) {
			throw new Error('배경 이미지 삭제에 실패했습니다.');
		}
		await loadGallery();
	} catch (err) {
		showStatus(err.message);
	}
}

function showStatus(message, isError = true) {
	statusMessage.textContent = message;
	statusMessage.classList.remove('error', 'success');
	statusMessage.classList.add(isError ? 'error' : 'success');
	statusMessage.hidden = false;
}

function hideStatus() {
	statusMessage.hidden = true;
}

const fontDropZone = document.getElementById('fontDropZone');
const fontFileInput = document.getElementById('fontFileInput');
const fontList = document.getElementById('fontList');
const fontEmptyMessage = document.getElementById('fontEmptyMessage');
const fontStatusMessage = document.getElementById('fontStatusMessage');

loadFontList();

fontDropZone.addEventListener('click', () => fontFileInput.click());

fontDropZone.addEventListener('dragover', (e) => {
	e.preventDefault();
	fontDropZone.classList.add('dragover');
});

fontDropZone.addEventListener('dragleave', () => {
	fontDropZone.classList.remove('dragover');
});

fontDropZone.addEventListener('drop', (e) => {
	e.preventDefault();
	fontDropZone.classList.remove('dragover');
	if (e.dataTransfer.files.length > 0) {
		uploadFont(e.dataTransfer.files[0]);
	}
});

fontFileInput.addEventListener('change', () => {
	if (fontFileInput.files.length > 0) {
		uploadFont(fontFileInput.files[0]);
	}
	fontFileInput.value = '';
});

async function uploadFont(file) {
	hideFontStatus();

	const formData = new FormData();
	formData.append('fontFile', file);

	try {
		const response = await fetch('/api/admin/font', {
			method: 'POST',
			body: formData
		});

		if (!response.ok) {
			const message = await response.text();
			throw new Error(message || '폰트 저장에 실패했습니다.');
		}

		showFontStatus('폰트가 추가되었습니다.', false);
		await loadFontList();
	} catch (err) {
		showFontStatus(err.message);
	}
}

async function loadFontList() {
	try {
		const response = await fetch('/api/admin/font');
		if (!response.ok) {
			throw new Error('폰트 목록을 불러오지 못했습니다.');
		}
		const fonts = await response.json();
		renderFontList(fonts);
	} catch (err) {
		showFontStatus(err.message);
	}
}

function renderFontList(fonts) {
	fontList.innerHTML = '';
	fontEmptyMessage.hidden = fonts.length > 0;

	for (const font of fonts) {
		const item = document.createElement('div');
		item.className = 'font-item';

		const preview = document.createElement('span');
		preview.className = 'font-preview';
		preview.textContent = font.name;
		item.appendChild(preview);

		const deleteBtn = document.createElement('button');
		deleteBtn.type = 'button';
		deleteBtn.className = 'delete-btn';
		deleteBtn.textContent = '×';
		deleteBtn.title = '삭제';
		deleteBtn.addEventListener('click', () => deleteFont(font.id));
		item.appendChild(deleteBtn);

		fontList.appendChild(item);

		const fontFamily = 'admin-font-preview-' + font.id;
		const fontFace = new FontFace(fontFamily, `url(${font.fontUrl}) format('${font.format}')`);
		fontFace.load().then((loaded) => {
			document.fonts.add(loaded);
			preview.style.fontFamily = `'${fontFamily}', sans-serif`;
		}).catch(() => {
			// 폰트 미리보기 로드에 실패해도 이름은 그대로 보여준다.
		});
	}
}

async function deleteFont(id) {
	hideFontStatus();
	try {
		const response = await fetch('/api/admin/font/' + id, { method: 'DELETE' });
		if (!response.ok) {
			throw new Error('폰트 삭제에 실패했습니다.');
		}
		await loadFontList();
	} catch (err) {
		showFontStatus(err.message);
	}
}

function showFontStatus(message, isError = true) {
	fontStatusMessage.textContent = message;
	fontStatusMessage.classList.remove('error', 'success');
	fontStatusMessage.classList.add(isError ? 'error' : 'success');
	fontStatusMessage.hidden = false;
}

function hideFontStatus() {
	fontStatusMessage.hidden = true;
}
