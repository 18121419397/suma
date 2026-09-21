document.querySelector('.upload').onclick=e=>{e.preventDefault();stop();Native.choosePhoto()};
$('export').onclick=()=>Native.exportText(($('title').value||'课文')+'.txt',$('title').value+'\n\n'+$('text').value);
window.nativeOcrStatus=msg=>{$('ocrStatus').textContent=msg};
window.nativeOcrResult=text=>{if(!text.trim()){nativeOcrStatus('没有识别到文字，请重拍清晰、正面的照片。');return}$('text').value=text;$('title').value='我的新课文';nativeOcrStatus('识别完成，请校对文字后点击“开始练习”。');toast('文字已提取，请先校对')};
window.nativePause=()=>{stop();appSpeech.cancel()};
