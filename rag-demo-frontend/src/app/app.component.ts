import { Component } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  selectedFile?: File;
  uploadMessage = '';
  query = '';
  results: any[] = [];

  constructor(private http: HttpClient) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  upload() {
    if (!this.selectedFile) return;

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    this.http.post('http://localhost:8080/upload', formData)
      .subscribe({
        next: () => this.uploadMessage = '✅ Upload thành công!',
        error: () => this.uploadMessage = '❌ Upload thất bại!'
      });
  }

  search() {
    if (!this.query.trim()) return;

    const params = new HttpParams().set('q', this.query);
    this.http.get<any[]>('http://localhost:8080/search', { params })
      .subscribe(data => this.results = data);
  }
}
