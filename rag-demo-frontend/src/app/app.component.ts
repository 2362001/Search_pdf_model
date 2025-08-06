import { HttpClient } from '@angular/common/http';
import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { NzModalService } from 'ng-zorro-antd/modal';
import { ModalComponent } from './modal/modal.component';
import { NzButtonSize } from 'ng-zorro-antd/button';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, OnDestroy {
  selectedFile: File | null = null;
  uploadMessage = '';
  query = '';
  results: any[] = [];
  uploadedFiles: any[] = [];

  scanInterval = 5000; // milliseconds (default 5s)
  scanIntervalInput = 5; // seconds
  intervalId: any;
  scanMessage = '';

  isHistoryVisible = false;
  searchHistory: string[] = [];
  historyTimestamps: { [query: string]: string } = {};
  size: NzButtonSize = 'large';
  constructor(private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private modal: NzModalService
  ) { }

  ngOnInit() {
    this.fetchUploadedFiles();
    this.startAutoFetch();
  }

  ngOnDestroy() {
    this.clearAutoFetch();
  }

  onSearchHistory() {
    this.modal.create({
      nzTitle: 'Thông báo',
      nzContent: '<p>Nội dung tạm để kiểm tra hiển thị</p>',
      nzFooter: null,
      nzClosable: true,
      nzMaskClosable: true
    });
  }

  startAutoFetch() {
    this.clearAutoFetch();
    this.intervalId = setInterval(() => {
      this.fetchUploadedFiles();
    }, this.scanInterval);
  }

  clearAutoFetch() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  updateScanInterval() {
    if (this.scanIntervalInput >= 1) {
      this.scanInterval = this.scanIntervalInput * 1000;
      this.startAutoFetch(); // restart interval with new time
    }
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  upload() {
    if (!this.selectedFile) return;

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    this.http.post('http://localhost:7654/upload', formData, { responseType: 'text' })
      .subscribe({
        next: () => {
          this.uploadMessage = 'Upload thành công!';
          this.selectedFile = null;
          this.fetchUploadedFiles();
        },
        error: () => {
          this.uploadMessage = 'Upload thất bại!';
        }
      });
  }

  search() {
    if (!this.query) return;

    // Gọi API tìm kiếm
    this.http.post<any[]>('http://localhost:7654/search', { query: this.query })
      .subscribe(res => {
        this.results = res;

        // Ghi lại lịch sử tìm kiếm
        if (!this.searchHistory.includes(this.query)) {
          this.searchHistory.unshift(this.query);
        }
        this.historyTimestamps[this.query] = new Date().toLocaleString();

        // Giới hạn lịch sử 10 mục gần nhất
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

          // Tự động ẩn thông báo sau 3 giây
          setTimeout(() => {
            this.scanMessage = '';
          }, 3000);
        },
        error: () => {
          this.scanMessage = 'Lỗi khi quét dữ liệu!';
          setTimeout(() => {
            this.scanMessage = '';
          }, 3000);
        }
      });
  }
}
