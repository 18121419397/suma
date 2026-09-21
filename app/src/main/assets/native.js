let activeRecognition=null;
class NativeRecognition {
 start(){activeRecognition=this;Native.startSpeech()}
 abort(){activeRecognition=null;Native.stopSpeech()}
}
window.SpeechRecognition=NativeRecognition;
window.nativeSpeech=(text,final)=>{const r=activeRecognition;if(!r)return;const item=[{transcript:text}];item.isFinal=final;r.onresult?.({resultIndex:0,results:[item]});if(final&&activeRecognition===r){activeRecognition=null;r.onend?.()}};
window.nativeSpeechError=code=>{const r=activeRecognition;activeRecognition=null;r?.onerror?.({error:code})};
window.nativeSpeechEnd=()=>{const r=activeRecognition;activeRecognition=null;r?.onend?.()};
window.AppUtterance=class{constructor(text){this.text=text;this.rate=.8}};
window.appSpeech={speaking:false,current:null,cancel(){Native.stopTts();this.speaking=false;this.current=null},speak(u){this.current=u;this.speaking=true;Native.speak(u.text,u.rate)}};
window.nativeTtsEnd=()=>{appSpeech.speaking=false;appSpeech.current?.onend?.();appSpeech.current=null};
window.nativeTtsError=()=>{appSpeech.speaking=false;appSpeech.current?.onerror?.();appSpeech.current=null;document.getElementById('voiceStatus').textContent='系统中文朗读服务不可用，请检查手机语音设置。'};
