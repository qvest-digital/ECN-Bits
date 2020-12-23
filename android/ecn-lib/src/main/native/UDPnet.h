#ifndef ECNBITS_UDPNET_H
#define ECNBITS_UDPNET_H

#ifndef ECNBITS_SKIP_DALVIK
extern jclass cls_STAG;		// dalvik.system.SocketTagger
extern jmethodID M_STAG_get;	// dalvik.system.SocketTagger::get()
extern jmethodID m_STAG_tag;	// dalvik.system.SocketTagger.tag()
#endif

#ifdef __cplusplus
extern "C" {
#endif

void tagSocket(JNIEnv *env, int fd);

#ifdef __cplusplus
}
#endif

#endif
