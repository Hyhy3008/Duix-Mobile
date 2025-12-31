//
//  GJLAudioPlayer.m
//  GJLocalDigitalSDK
//
//  Created by guiji on 2024/5/21.
//
#define KFDecoderMaxCache 4096 * 5 // 解码数据缓冲区最大长度。

#import "GJLAudioPlayer.h"

#import "GJLKFAudioCapture.h"
#import "GJLGCDTimer.h"
//#import "GJLKFAudioRender.h"
#import "DigitalHumanDriven.h"
#import "GJLDigitalManager.h"
@interface GJLAudioPlayer ()


//@property (nonatomic, strong) NSMutableData *pcmDataCache2; // 解码数据缓冲区。
@property (nonatomic, strong) GJLKFAudioConfig *audioConfig;
//@property (nonatomic, strong) GJLKFAudioRender *audioRender;
@property (nonatomic, strong) dispatch_semaphore_t semaphore;
@property (nonatomic, strong) GJLKFAudioCapture *audioCapture;
@property (nonatomic, assign)NSInteger all_pcm_lenth;
@property (nonatomic, strong)NSLock * pcmLock;
@property (nonatomic, assign)BOOL isBreak;
@end
static GJLAudioPlayer * manager = nil;
@implementation GJLAudioPlayer
+ (GJLAudioPlayer *)manager
{
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        manager = [[GJLAudioPlayer alloc] init];
    });
    return manager;
}
-(id)init
{
    self=[super init];
    if(self)
    {
        self.pcmLock=[[NSLock alloc] init];
        _semaphore = dispatch_semaphore_create(1);
        _pcmDataCache = [[NSMutableData alloc] init];
//        _pcmDataCache2 = [[NSMutableData alloc] init];
 
//        self.asset_timer_queue=dispatch_queue_create("com.digitalsdk.asset_timer_queue", DISPATCH_QUEUE_CONCURRENT);

//        [self setupAudioSession];
        self.isMute=NO;
        self.isEnableRecord=YES;
        self.volume=1.0;
        self.isVoiceProcessingIO=YES;
        self.isFadeInOut=YES;
  
        
    }
    return self;
}
-(void)toStartRunning
{
    if(!self.audioCapture.isRunning)
    {
        [self.audioCapture startRunning];
    }

   
}

#pragma mark - Property
- (GJLKFAudioConfig *)audioConfig {
    if (!_audioConfig) {
        _audioConfig = [GJLKFAudioConfig defaultConfig];
    }
    
    return _audioConfig;
}

- (GJLKFAudioCapture *)audioCapture {
    if (!_audioCapture) {
        __weak typeof(self) weakSelf = self;
        _audioCapture = [[GJLKFAudioCapture alloc] initWithConfig:self.audioConfig];
        _audioCapture.errorCallBack = ^(NSError* error) {
            NSLog(@"KFAudioCapture error: %zi %@", error.code, error.localizedDescription);
        };
        _audioCapture.playFailed = ^{
                    if(weakSelf.playFailed)
                    {
                        weakSelf.playFailed();
                    }
                };
        _audioCapture.playEnd = ^{
                    if(weakSelf.playEnd)
                    {
         
                        weakSelf.playEnd();
                    }
                };
        _audioCapture.playProgress = ^(float current, float total) {
                    if(weakSelf.playProgress)
                    {
                        weakSelf.playProgress(current, total);
                    }
                };
        _audioCapture.audioPCMDataCallBack = ^(NSData * _Nonnull data, NSInteger level) {
            if(weakSelf.audioPCMDataCallBack)
            {
                weakSelf.audioPCMDataCallBack(data, level);
            }
          //  [weakSelf voiceRecorded:data];
        };
        
        _audioCapture.audioBufferInputCallBack = ^(AudioBufferList * _Nonnull audioBufferList) {
            dispatch_semaphore_wait(weakSelf.semaphore, DISPATCH_TIME_FOREVER);
            if (weakSelf.pcmDataCache.length < audioBufferList->mBuffers[0].mDataByteSize || weakSelf.isPlaying==NO || [DigitalHumanDriven manager].isAudioReady==0 || [GJLAudioPlayer manager].isMute==YES) {
               
                     for (UInt32 i=0; i < audioBufferList->mNumberBuffers; i++)
                     {
                         if (audioBufferList->mBuffers[i].mData != NULL) {
                             memset(static_cast<int8_t*>(audioBufferList->mBuffers[i].mData), 0.0, audioBufferList->mBuffers[i].mDataByteSize);
                         }
                     }
        
//
                     

                     
                  
            } else {
                
                
                //                         dispatch_semaphore_wait(weakSelf.semaphore, DISPATCH_TIME_FOREVER);
                memcpy(audioBufferList->mBuffers[0].mData, weakSelf.pcmDataCache.bytes, audioBufferList->mBuffers[0].mDataByteSize);
                if(weakSelf.pcmDataCache.length>audioBufferList->mBuffers[0].mDataByteSize)
                {
                    [weakSelf.pcmDataCache replaceBytesInRange:NSMakeRange(0, audioBufferList->mBuffers[0].mDataByteSize) withBytes:NULL length:0];
                }
                else
                {
                    [weakSelf.pcmDataCache replaceBytesInRange:NSMakeRange(0, weakSelf.pcmDataCache.length) withBytes:NULL length:0];
                }
             
    
               
//                         dispatch_semaphore_signal(weakSelf.semaphore);
                     }
              [GJLDigitalManager manager].dataLength=weakSelf.pcmDataCache.length;
               dispatch_semaphore_signal(weakSelf.semaphore);
             };
    
        _audioCapture.sampleBufferOutputCallBack = ^(CMSampleBufferRef  _Nonnull sample, NSInteger type) {
            if(weakSelf.sampleBufferOutputCallBack)
            {
                weakSelf.sampleBufferOutputCallBack(sample, type);
            }
        };
    }
    
    return _audioCapture;
}
NSData *createSilentAudioData(NSTimeInterval duration, int sampleRate, int bitDepth, int channels) {
    // 计算每个样本的字节数
    int bytesPerSample = bitDepth / 8 * channels;
      
    // 计算总样本数
    int totalSamples = (int)(sampleRate * duration);
      
    // 计算总字节数
    size_t totalBytes = totalSamples * bytesPerSample;
      
    // 分配内存并初始化为0（静音）
    void *silentAudioBytes = calloc(1, totalBytes);
    if (!silentAudioBytes) {
        return nil; // 内存分配失败
    }
      
    // 将内存包装成NSData对象
    NSData *silentAudioData = [NSData dataWithBytes:silentAudioBytes length:totalBytes];
      
    // 释放临时内存
    free(silentAudioBytes);
      
    return silentAudioData;
}
NSData *audioBufferListToNSData(AudioBufferList *audioBufferList) {
  // 计算总的数据大小
  NSUInteger totalSize = 0;
  for (int i = 0; i < audioBufferList->mNumberBuffers; i++) {
      totalSize += audioBufferList->mBuffers[i].mDataByteSize;
  }
    
  // 分配一块足够大的内存来存放所有数据
  void *data = malloc(totalSize);
  if (!data) {
      return nil; // 内存分配失败
  }
    
  // 复制数据
  NSUInteger offset = 0;
  for (int i = 0; i < audioBufferList->mNumberBuffers; i++) {
      AudioBuffer audioBuffer = audioBufferList->mBuffers[i];
      memcpy((char *)data + offset, audioBuffer.mData, audioBuffer.mDataByteSize);
      offset += audioBuffer.mDataByteSize;
  }
    
  // 创建NSData对象
  NSData *nsData = [NSData dataWithBytesNoCopy:data length:totalSize freeWhenDone:YES];
    
  return nsData;
}
- (void)setupAudioSession {
    NSError *error = nil;
    
    // 1、获取音频会话实例。
    AVAudioSession *session = [AVAudioSession sharedInstance];

    // 2、设置分类和选项。
    [session setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error:&error];
    if (error) {
        NSLog(@"AVAudioSession setCategory error.");
        error = nil;
        return;
    }
    

    // 4、激活会话。
    [session setActive:YES error:&error];
    if (error) {
        NSLog(@"AVAudioSession setActive error.");
        error = nil;
        return;
    }
 //   [[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:nil];
     UInt32 doChangeDefault = 1;
      AudioSessionSetProperty(kAudioSessionProperty_OverrideCategoryDefaultToSpeaker, sizeof(doChangeDefault), &doChangeDefault);
}








-(void)resetTime
{


}
- (void)startPlaying
{
    self.isPlaying=YES;

    [self.audioCapture startPlaying];

}
- (void)stopPlaying:(void (^)( BOOL isSuccess))success
{
    self.isPlaying=NO;
//    [self toStopAudioTimer];
//    if(self.assetReader!=nil)
//    {
//        [self.assetReader toCancelRead];
//        self.assetReader=nil;
//    }
    __weak typeof(self) weakSelf = self;
    [self.audioCapture stopPlaying:^(BOOL isSuccess) {
//        weakSelf.pcmDataCache=[NSMutableData data];
        success(isSuccess);
    }];

    
}
-(void)pauseRunning:(BOOL)pause
{
    [self.audioCapture pauseRunning:pause];
}
-(void)pause
{
    self.isPlaying=NO;
//    [self.audioRender pause];
}
-(void)toFree
{
 
    [self.audioCapture stopRunning];
    [self.audioCapture toFree];

}

static void prv_rtc_resample_48k_to_16k(const uint8_t *src_data, int len,
                                        int channel, uint8_t *dst_data) {
  const int16_t *src_data_index = (const int16_t *)src_data;
  int16_t *dst_data_index = (int16_t *)dst_data;
  const int samples = len / (3 * channel * (sizeof(int16_t) * sizeof(uint8_t)));
  for (int i = 0; i < samples; i++) {
    for (int j = 0; j < channel; j++) {
      dst_data_index[i * channel + j] =
          (src_data_index[i * 3 * channel + j * 3 + 0] +
           src_data_index[i * 3 * channel + j * 3 + 1] +
           src_data_index[i * 3 * channel + j * 3 + 2]) /3;
    }
  }
}

std::vector<int16_t> resample_pcm(const std::vector<int16_t>& input, size_t new_sample_rate) {
    if (new_sample_rate <= 0 || input.empty()) return {};
 
    size_t old_sample_rate = 48000;
    size_t factor = old_sample_rate / new_sample_rate;
    size_t new_size = input.size() / factor;
 
    std::vector<int16_t> output(new_size);
    for (size_t i = 0; i < new_size; ++i) {
        size_t src_index = i * factor;
        output[i] = input[src_index];
    }
 
    return output;
}

-(void)toWavPcmData:(NSData*)audioData
{
    if([DigitalHumanDriven manager].isStop)
    {
        return;
    }
    if([DigitalHumanDriven manager].sessid<=0)
    {
        return;
    }
    [self.pcmLock lock];
    int dataLength =(int) [audioData length];
    
    // 动态分配uint8_t数组
    uint8_t *byteArray = (uint8_t *)malloc(dataLength * sizeof(uint8_t));
    
        // 复制数据到uint8_t数组
    [audioData getBytes:byteArray range:NSMakeRange(0, dataLength)];
        //推流pcm
    [[DigitalHumanDriven manager] wavPCM:byteArray size:dataLength];
    dispatch_semaphore_wait(self.semaphore, DISPATCH_TIME_FOREVER);
    [self.pcmDataCache appendData:audioData];
    self.all_pcm_lenth=self.all_pcm_lenth+dataLength;
   
    [self toBufferLength:(int)self.all_pcm_lenth isFinish:YES];
    
    dispatch_semaphore_signal(self.semaphore);
    audioData=nil;
    free(byteArray); // 释放内存
    byteArray = NULL; // 避免悬垂指针
    [self.pcmLock unlock];
   
}
-(void)wavPCM:(uint8_t*)pcm size:(int)size
{
    if([DigitalHumanDriven manager].isStop)
    {
        return;
    }
    if([DigitalHumanDriven manager].sessid<=0)
    {
        return;
    }
         [self.pcmLock lock];
//        dispatch_semaphore_wait(self.semaphore, DISPATCH_TIME_FOREVER);
         [[DigitalHumanDriven manager] wavPCM:pcm size:size];
         NSData *data = [NSData dataWithBytes:pcm length:size];
         dispatch_semaphore_wait(self.semaphore, DISPATCH_TIME_FOREVER);
         [self.pcmDataCache appendData:data];
         self.all_pcm_lenth=self.all_pcm_lenth+size;
        
         [self toBufferLength:(int)self.all_pcm_lenth isFinish:YES];
          dispatch_semaphore_signal(self.semaphore);
         data=nil;
         [self.pcmLock unlock];


   
    

}
-(void)toStopRunning
{
    self.isPlaying=NO;
    self.isPlayMutePcm=NO;
    dispatch_semaphore_wait(self.semaphore, DISPATCH_TIME_FOREVER);
    _pcmDataCache = [[NSMutableData alloc] init];
    dispatch_semaphore_signal(self.semaphore);
    [self.audioCapture stopRunning];
}
/*
*dataLength 音频buffer的长度
*isFinish 是否一句话结束或者整段话结束
*/
-(void)toBufferLength:(int)dataLength isFinish:(BOOL)isFinish
{
    [self.audioCapture toBufferLength:dataLength isFinish:isFinish];
}
-(void)clearAudioBuffer
{

    dispatch_semaphore_wait(self.semaphore, DISPATCH_TIME_FOREVER);
    self.isBreak=YES;
   if(self.pcmDataCache.length>0)
   {
       [self.pcmDataCache replaceBytesInRange:NSMakeRange(0, self.pcmDataCache.length) withBytes:NULL length:0];
   }
//    _pcmDataCache = [[NSMutableData alloc] init];
    self.all_pcm_lenth=0;
    self.audioCapture.readedSize=0;
    self.audioCapture.dataLengh=0;
     dispatch_semaphore_signal(self.semaphore);
    [self.audioCapture resetAudioUnit];


}
-(void)toEnableRecord:(BOOL)isEnable
{
    self.isEnableRecord=isEnable;
//    [self.audioCapture toMuteRecord:!isEnable];
    
}
-(void)toMuteRecord:(BOOL)isMute
{
    [self.audioCapture toMuteRecord:isMute];
}
@end
