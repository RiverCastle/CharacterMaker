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
