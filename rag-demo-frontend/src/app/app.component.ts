import { HttpClient } from '@angular/common/http';
import { Component, OnInit, OnDestroy } from '@angular/core';

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
  intervalId: any;

  constructor(private http: HttpClient) { }

  ngOnInit() {
    this.fetchUploadedFiles();

    // Gọi lại API mỗi 5 giây
    this.intervalId = setInterval(() => {
      this.fetchUploadedFiles();
    }, 10000);
  }

  ngOnDestroy() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
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

    this.http.post<any[]>('http://localhost:7654/search', { query: this.query })
      .subscribe(res => {
        this.results = res;
      });
  }

  fetchUploadedFiles() {
    this.http.get<any[]>('http://localhost:7654/files')
      .subscribe(res => {
        this.uploadedFiles = res;
      });
  }
}
