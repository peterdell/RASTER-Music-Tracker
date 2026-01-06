#include "RmtTest.h"

#include "Rmt.h"

#include "wasap.h"
CRmtTest::CRmtTest() {
};

void CRmtTest::RunFor(const CRmtApp& app, const CString fileName) {
    int sizeOfString = (fileName.GetLength() + 1);
    LPTSTR lpsz = new TCHAR[sizeOfString];
    _tcscpy_s(lpsz, sizeOfString, fileName);
    //... modify lpsz as much as you want   
    WASAP_WinMain(app.m_hInstance, NULL, lpsz, 0);
    delete lpsz;
}