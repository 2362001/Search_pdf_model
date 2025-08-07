import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    FormsModule, // ✅ cần cho [(ngModel)]
    NzButtonModule,
    CommonModule,
    NzModalModule,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);
  private modal = inject(NzModalService);

  selectedFile: File | null = null;
  uploadMessage = '';
  query = '';
  results: any[] = [];
  uploadedFiles: any[] = [];
  uploadProgress = 0; // %
  isUploading = false;
  progressTimer: any;

  scanInterval = 150000;
  scanIntervalInput = 150;
  intervalId: any;
  scanMessage = '';

  isHistoryVisible = false;
  searchHistory: string[] = [];
  historyTimestamps: { [query: string]: string } = {};

  ngOnInit() {
    this.fetchUploadedFiles();
    this.startAutoFetch();
  }

  ngOnDestroy() {
    this.clearAutoFetch();
  }

  onSearchHistory() {
    this.modal.create({
      nzTitle: 'Lịch sử tìm kiếm',
      nzContent: this.generateHistoryHtml(),
      nzFooter: null,
      nzClosable: true,
      nzMaskClosable: true,
    });
  }

  generateHistoryHtml(): string {
    if (this.searchHistory.length === 0) {
      return '<p>Chưa có lịch sử tìm kiếm.</p>';
    }

    return `
      <ul>
        ${this.searchHistory
        .map(query => `<li>${query} - ${this.historyTimestamps[query]}</li>`)
        .join('')}
      </ul>
    `;
  }

  startAutoFetch() {
    this.clearAutoFetch();
    this.intervalId = setInterval(() => {
      this.fetchUploadedFiles();
    }, this.scanInterval);
  }

  clearAutoFetch() {
    if (this.intervalId) clearInterval(this.intervalId);
  }

  updateScanInterval() {
    if (this.scanIntervalInput >= 1) {
      this.scanInterval = this.scanIntervalInput * 1000;
      this.startAutoFetch();
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile = input.files[0];
    }
  }

  upload() {
    if (!this.selectedFile) return;

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    this.isUploading = true;
    this.uploadProgress = 0;

    // Khởi chạy progress giả
    this.startFakeProgress();

    this.http.post('http://localhost:7654/upload', formData, {
      reportProgress: true,
      observe: 'events',
      responseType: 'text'
    }).subscribe({
      next: event => {
        if (event.type === HttpEventType.Response) {
          // Khi upload xong
          this.completeProgressBar();
          this.uploadMessage = 'Upload thành công!';
          this.selectedFile = null;
          this.fetchUploadedFiles();
        }
      },
      error: () => {
        this.resetProgressBar();
        this.uploadMessage = 'Upload thất bại!';
      }
    });
  }

  startFakeProgress() {
    // Tăng dần từ 0 đến 90%
    this.progressTimer = setInterval(() => {
      if (this.uploadProgress < 90) {
        this.uploadProgress += 1;
      }
    }, 100); // Mỗi 100ms tăng 1%
  }

  completeProgressBar() {
    clearInterval(this.progressTimer);
    const interval = setInterval(() => {
      this.uploadProgress += 2; // tăng nhanh hơn một chút
      if (this.uploadProgress >= 100) {
        clearInterval(interval);
        setTimeout(() => {
          this.isUploading = false;
          this.uploadProgress = 0;
        }, 500); // delay một chút rồi ẩn
      }
    }, 30);
  }

  resetProgressBar() {
    clearInterval(this.progressTimer);
    this.isUploading = false;
    this.uploadProgress = 0;
  }

  search() {
    // if (!this.query) return;

    this.http.post<any[]>('http://localhost:7654/search', { query: this.query })
      .subscribe(res => {
        this.results = res;

        if (!this.searchHistory.includes(this.query)) {
          this.searchHistory.unshift(this.query);
        }
        this.historyTimestamps[this.query] = new Date().toLocaleString();

        if (this.searchHistory.length > 10) {
          const removed = this.searchHistory.pop();
          if (removed) delete this.historyTimestamps[removed];
        }
      });
  }

  fetchUploadedFiles() {
    this.http.get<any[]>('http://localhost:7654/files')
      .subscribe({
        next: res => {
          this.uploadedFiles = res;
          this.scanMessage = `Đã quét dữ liệu lúc ${new Date().toLocaleTimeString()}`;

          setTimeout(() => {
            this.scanMessage = '';
            this.cdr.markForCheck();
          }, 3000);
        },
        error: () => {
          this.scanMessage = 'Lỗi khi quét dữ liệu!';
          setTimeout(() => {
            this.scanMessage = '';
            this.cdr.markForCheck();
          }, 3000);
        }
      });
  }
}
